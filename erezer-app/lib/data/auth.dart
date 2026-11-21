import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'api_client.dart';
import 'models.dart';

/// A logged-in customer: the same account as on the website.
class Session {
  const Session({
    required this.userId,
    required this.email,
    required this.firstName,
    required this.lastName,
    required this.emailVerified,
    required this.accessToken,
    required this.refreshToken,
    required this.expiresAt,
  });

  factory Session.from(AuthTokens t) => Session(
    userId: t.userId,
    email: t.email,
    firstName: t.firstName,
    lastName: t.lastName,
    emailVerified: t.emailVerified,
    accessToken: t.accessToken,
    refreshToken: t.refreshToken,
    expiresAt: DateTime.now().add(Duration(seconds: t.expiresInSeconds)),
  );

  factory Session.fromJson(Map<String, dynamic> j) => Session(
    userId: j['userId'] as String,
    email: j['email'] as String? ?? '',
    firstName: j['firstName'] as String? ?? '',
    lastName: j['lastName'] as String? ?? '',
    emailVerified: j['emailVerified'] as bool? ?? false,
    accessToken: j['accessToken'] as String? ?? '',
    refreshToken: j['refreshToken'] as String? ?? '',
    expiresAt: DateTime.tryParse(j['expiresAt'] as String? ?? '') ?? DateTime.now(),
  );

  Map<String, dynamic> toJson() => {
    'userId': userId,
    'email': email,
    'firstName': firstName,
    'lastName': lastName,
    'emailVerified': emailVerified,
    'accessToken': accessToken,
    'refreshToken': refreshToken,
    'expiresAt': expiresAt.toIso8601String(),
  };

  final String userId;
  final String email;
  final String firstName;
  final String lastName;

  /// Only a customer who confirmed their email may order while logged in.
  final bool emailVerified;
  final String accessToken;
  final String refreshToken;
  final DateTime expiresAt;

  String get name => '$firstName $lastName'.trim();
  bool get expiresSoon => DateTime.now().isAfter(expiresAt.subtract(const Duration(seconds: 30)));
}

/// The login, kept in the phone's secure storage so the customer stays logged in.
class AuthNotifier extends Notifier<Session?> {
  static const _storageKey = 'erezer.session.v1';
  static const _storage = FlutterSecureStorage();
  Future<Session>? _refreshing;

  @override
  Session? build() {
    _load();
    return null;
  }

  ApiClient get _api => ref.read(apiProvider);

  Future<void> _load() async {
    try {
      final raw = await _storage.read(key: _storageKey);
      if (raw != null && state == null) state = Session.fromJson(jsonDecode(raw) as Map<String, dynamic>);
    } catch (_) {
      // An unreadable saved login just means logging in again.
    }
  }

  Future<void> _keep(Session? s) async {
    state = s;
    try {
      if (s == null) {
        await _storage.delete(key: _storageKey);
      } else {
        await _storage.write(key: _storageKey, value: jsonEncode(s.toJson()));
      }
    } catch (_) {}
  }

  Future<void> login(String email, String password) async => _keep(Session.from(await _api.login(email, password)));

  Future<void> register({
    required String firstName,
    required String lastName,
    required String email,
    required String password,
  }) async => _keep(
    Session.from(await _api.register(firstName: firstName, lastName: lastName, email: email, password: password)),
  );

  Future<void> logout() => _keep(null);

  /// A fresh login from the server, which also says whether the email is confirmed by now.
  Future<Session> refresh() {
    final current = state;
    if (current == null) return Future.error(ApiException('Please log in.', statusCode: 401));
    // Several calls at once share one refresh.
    return _refreshing ??= () async {
      try {
        final next = Session.from(await _api.refresh(current.refreshToken));
        await _keep(next);
        return next;
      } on ApiException catch (e) {
        // The server no longer accepts this login.
        if (e.statusCode == 400 || e.statusCode == 401) {
          await _keep(null);
          throw ApiException('Your login has expired. Please log in again.', statusCode: 401);
        }
        rethrow;
      } finally {
        _refreshing = null;
      }
    }();
  }

  /// Runs a logged-in request, renewing the login first when it is about to
  /// expire, and once more if the server turns it away.
  Future<T> call<T>(Future<T> Function(ApiClient api, Session s) request) async {
    var s = state;
    if (s == null) throw ApiException('Please log in.', statusCode: 401);
    if (s.expiresSoon) s = await refresh();
    try {
      return await request(_api, s);
    } on ApiException catch (e) {
      if (e.statusCode != 401) rethrow;
      return request(_api, await refresh());
    }
  }
}

final authProvider = NotifierProvider<AuthNotifier, Session?>(AuthNotifier.new);
