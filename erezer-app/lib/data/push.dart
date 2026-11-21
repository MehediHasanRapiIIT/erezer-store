import 'dart:async';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'api_client.dart';
import 'auth.dart';

/// Whether this phone can get order notifications.
class PushState {
  const PushState({this.available = false, this.allowed = false, this.token});

  /// Firebase is set up in this build (google-services.json was there).
  final bool available;

  /// The customer allowed notifications.
  final bool allowed;

  /// This phone's Firebase token, which the shop's server sends to.
  final String? token;

  PushState copyWith({bool? allowed, String? token}) =>
      PushState(available: available, allowed: allowed ?? this.allowed, token: token ?? this.token);
}

/// Order updates on the phone ("Order EZ-123456 has shipped"). The shop's
/// server sends them through Firebase to the phone that placed the order and to
/// every phone the customer is logged in on. Tapping one opens the order.
class PushNotifier extends Notifier<PushState> {
  static const _channel = AndroidNotificationChannel(
    'orders',
    'Order updates',
    description: 'When your order is confirmed, shipped and delivered.',
    importance: Importance.high,
  );

  final _local = FlutterLocalNotificationsPlugin();
  GoRouter? _router;

  @override
  PushState build() {
    // Logging in ties this phone to the account, so that account's orders reach it.
    // Logging out: this phone stops getting that account's updates. The old login
    // still works on the server for this one request.
    ref.listen(authProvider, (before, now) {
      if (now != null && before?.userId != now.userId) _link();
      if (before != null && now == null) unlink(before);
    });
    return const PushState();
  }

  ApiClient get _api => ref.read(apiProvider);

  /// Called once at start-up. Without Firebase in this build it does nothing.
  Future<void> start(GoRouter router) async {
    _router = router;
    try {
      await Firebase.initializeApp();
    } catch (e) {
      debugPrint('Order notifications off: Firebase is not set up ($e)');
      return;
    }
    final messaging = FirebaseMessaging.instance;
    try {
      await _local.initialize(
        settings: const InitializationSettings(android: AndroidInitializationSettings('ic_notification')),
        onDidReceiveNotificationResponse: (r) => _openPayload(r.payload),
      );
      await _local
          .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
          ?.createNotificationChannel(_channel);
      final settings = await messaging.getNotificationSettings();
      // The registration token is what the server's FCM HTTP v1 sending addresses;
      // the newer installation-ID sending (register()/onRegistered) isn't used there.
      // ignore: deprecated_member_use
      final token = await messaging.getToken();
      state = PushState(
        available: true,
        allowed: settings.authorizationStatus == AuthorizationStatus.authorized,
        token: token,
      );
      await _register();
      // ignore: deprecated_member_use
      messaging.onTokenRefresh.listen((t) {
        state = state.copyWith(token: t);
        _register();
      });
      // With the app open, Android doesn't show the notification itself: show it here.
      FirebaseMessaging.onMessage.listen(_showWhileOpen);
      FirebaseMessaging.onMessageOpenedApp.listen((m) => _open(m.data));
      final first = await messaging.getInitialMessage();
      if (first != null) _open(first.data);
    } catch (e) {
      debugPrint('Order notifications could not start: $e');
    }
  }

  /// Asks the customer (Android 13+) to allow notifications. True when allowed.
  Future<bool> askPermission() async {
    if (!state.available) return false;
    final settings = await FirebaseMessaging.instance.requestPermission();
    final allowed = settings.authorizationStatus == AuthorizationStatus.authorized;
    state = state.copyWith(allowed: allowed);
    return allowed;
  }

  Future<void> _register() async {
    final token = state.token;
    if (token == null) return;
    try {
      await _api.registerDevice(token);
    } catch (_) {
      // Tried again on the next start.
    }
    await _link();
  }

  Future<void> _link() async {
    final token = state.token;
    if (token == null || ref.read(authProvider) == null) return;
    try {
      await ref.read(authProvider.notifier).call((api, s) => api.linkDevice(s.userId, s.accessToken, token));
    } catch (_) {}
  }

  /// Before logging out: this phone stops getting that account's updates.
  Future<void> unlink(Session s) async {
    final token = state.token;
    if (token == null) return;
    try {
      await _api.unlinkDevice(s.userId, s.accessToken, token).timeout(const Duration(seconds: 5));
    } catch (_) {}
  }

  Future<void> _showWhileOpen(RemoteMessage m) async {
    final n = m.notification;
    if (n == null) return;
    await _local.show(
      id: m.hashCode,
      title: n.title,
      body: n.body,
      notificationDetails: NotificationDetails(
        android: AndroidNotificationDetails(
          _channel.id,
          _channel.name,
          channelDescription: _channel.description,
          importance: Importance.high,
          priority: Priority.high,
          icon: 'ic_notification',
        ),
      ),
      payload: _payloadOf(m.data),
    );
  }

  static String _payloadOf(Map<String, dynamic> data) =>
      '${data['guest'] ?? 'true'}|${data['orderId'] ?? ''}|${data['orderNumber'] ?? ''}';

  void _openPayload(String? payload) {
    final parts = (payload ?? '').split('|');
    if (parts.length < 3) return;
    _open({'guest': parts[0], 'orderId': parts[1], 'orderNumber': parts[2]});
  }

  /// A logged-in customer's order opens in My orders; anything else on Track an order.
  void _open(Map<String, dynamic> data) {
    final router = _router;
    if (router == null) return;
    final id = '${data['orderId'] ?? ''}';
    final number = '${data['orderNumber'] ?? ''}';
    final loggedIn = ref.read(authProvider) != null;
    if (data['guest'] != 'true' && loggedIn && id.isNotEmpty) {
      router.go('/account/orders/$id');
    } else if (number.isNotEmpty) {
      router.go('/account/track?number=${Uri.encodeQueryComponent(number)}');
    }
  }
}

final pushProvider = NotifierProvider<PushNotifier, PushState>(PushNotifier.new);
