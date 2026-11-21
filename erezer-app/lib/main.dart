import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import 'app/router.dart';
import 'app/theme.dart';
import 'data/push.dart';
import 'data/theme_mode.dart';

void main() {
  runApp(const ProviderScope(retry: noRetry, child: ErezerApp()));
}

/// A failed load shows "Try again" at once instead of retrying out of sight.
Duration? noRetry(int retryCount, Object error) => null;

class ErezerApp extends ConsumerStatefulWidget {
  const ErezerApp({super.key});

  @override
  ConsumerState<ErezerApp> createState() => _ErezerAppState();
}

class _ErezerAppState extends ConsumerState<ErezerApp> {
  final router = createRouter();
  final _day = buildTheme(dark: false);
  final _dark = buildTheme(dark: true);

  @override
  void initState() {
    super.initState();
    // Order notifications: off by themselves when Firebase isn't set up.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) ref.read(pushProvider.notifier).start(router);
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
      theme: _day,
      darkTheme: _dark,
      themeMode: ref.watch(themeModeProvider),
      routerConfig: router,
      builder: (context, child) => _Palette(child: child!),
    );
  }
}

/// Keeps [AppColors] on the theme in use (day or dark, the customer's choice or
/// the phone's), and repaints every page when it changes.
class _Palette extends StatefulWidget {
  const _Palette({required this.child});
  final Widget child;

  @override
  State<_Palette> createState() => _PaletteState();
}

class _PaletteState extends State<_Palette> {
  bool? _shown;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    AppColors.dark = dark;
    if (_shown != null && _shown != dark) {
      // Pages that didn't depend on the theme keep their old colours unless asked again.
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        void again(Element e) {
          e.markNeedsBuild();
          e.visitChildren(again);
        }

        (context as Element).visitChildren(again);
      });
    }
    _shown = dark;
    return widget.child;
  }
}
