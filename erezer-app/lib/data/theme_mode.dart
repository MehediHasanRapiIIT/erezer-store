import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Day or dark, as the customer chose in the menu; until they choose, the
/// phone's own setting (like the website). Kept on the phone.
class ThemeModeNotifier extends Notifier<ThemeMode> {
  static const _storageKey = 'erezer.theme';

  @override
  ThemeMode build() {
    _load();
    return ThemeMode.system;
  }

  Future<void> _load() async {
    try {
      final saved = (await SharedPreferences.getInstance()).getString(_storageKey);
      if (saved == 'dark') state = ThemeMode.dark;
      if (saved == 'light') state = ThemeMode.light;
    } catch (_) {}
  }

  void set(ThemeMode mode) {
    state = mode;
    SharedPreferences.getInstance().then((p) => p.setString(_storageKey, mode == ThemeMode.dark ? 'dark' : 'light'));
  }
}

final themeModeProvider = NotifierProvider<ThemeModeNotifier, ThemeMode>(ThemeModeNotifier.new);
