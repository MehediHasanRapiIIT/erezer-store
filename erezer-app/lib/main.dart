import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import 'app/router.dart';
import 'app/theme.dart';
import 'data/push.dart';

void main() {
  runApp(const ProviderScope(retry: noRetry, child: ErezerApp()));
}

/// A failed load shows "Try again" at once instead of retrying out of sight.
Duration? noRetry(int retryCount, Object error) => null;

class ErezerApp extends StatefulWidget {
  const ErezerApp({super.key});

  @override
  State<ErezerApp> createState() => _ErezerAppState();
}

class _ErezerAppState extends State<ErezerApp> {
  final router = createRouter();

  @override
  void initState() {
    super.initState();
    // Order notifications: off by themselves when Firebase isn't set up.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) ProviderScope.containerOf(context, listen: false).read(pushProvider.notifier).start(router);
    });
  }

  @override
  void dispose() {
    router.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'Erezer',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(),
      routerConfig: router,
    );
  }
}
