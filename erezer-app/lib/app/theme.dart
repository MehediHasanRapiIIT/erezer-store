import 'package:google_fonts/google_fonts.dart';
import 'package:material_ui/material_ui.dart';

/// Erezer's colours: black and white like the website, in day and dark. The
/// active set follows the app's theme ([AppColors.dark] is kept in step by the
/// app's root), so every page reads the right one.
class AppColors {
  static bool dark = false;

  /// Text, icons, and the "black" of buttons: white in dark.
  static Color get ink => dark ? const Color(0xFFF5F5F5) : const Color(0xFF0A0A0A);

  /// Text and icons on [ink] (a button's words).
  static Color get onInk => dark ? const Color(0xFF0A0A0A) : Colors.white;
  static Color get muted => dark ? const Color(0xFFA3A3A3) : const Color(0xFF525252);
  static Color get line => dark ? const Color(0xFF2E2E2E) : const Color(0xFFE5E5E5);
  static Color get page => dark ? const Color(0xFF0A0A0A) : const Color(0xFFFAFAFA);
  static Color get card => dark ? const Color(0xFF171717) : Colors.white;

  /// Light grey panels and picture placeholders.
  static Color get soft => dark ? const Color(0xFF1F1F1F) : const Color(0xFFF5F5F5);

  /// Blocks that stay black in both themes (the flash sale card), as on the website.
  static const black = Color(0xFF171717);
}

ThemeData buildTheme({required bool dark}) {
  AppColors.dark = dark;
  final base = ThemeData(
    useMaterial3: true,
    brightness: dark ? Brightness.dark : Brightness.light,
    colorScheme: ColorScheme.fromSeed(
      seedColor: AppColors.ink,
      brightness: dark ? Brightness.dark : Brightness.light,
      primary: AppColors.ink,
      onPrimary: AppColors.onInk,
      surface: AppColors.page,
      onSurface: AppColors.ink,
    ),
    scaffoldBackgroundColor: AppColors.page,
  );
  return base.copyWith(
    textTheme: GoogleFonts.interTextTheme(base.textTheme).apply(bodyColor: AppColors.ink, displayColor: AppColors.ink),
    appBarTheme: AppBarTheme(
      backgroundColor: AppColors.page,
      foregroundColor: AppColors.ink,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: true,
    ),
    cardTheme: CardThemeData(
      color: AppColors.card,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: AppColors.line),
      ),
    ),
    dividerColor: AppColors.line,
    dialogTheme: DialogThemeData(backgroundColor: AppColors.card),
    bottomSheetTheme: BottomSheetThemeData(backgroundColor: AppColors.card),
    drawerTheme: DrawerThemeData(backgroundColor: AppColors.card),
    snackBarTheme: SnackBarThemeData(
      backgroundColor: dark ? const Color(0xFF262626) : const Color(0xFF262626),
      contentTextStyle: const TextStyle(color: Colors.white),
      actionTextColor: const Color(0xFF93C5FD),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: AppColors.ink,
        foregroundColor: AppColors.onInk,
        shape: const StadiumBorder(),
        padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 14),
        textStyle: const TextStyle(fontWeight: FontWeight.w600),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(style: OutlinedButton.styleFrom(foregroundColor: AppColors.ink)),
    textButtonTheme: TextButtonThemeData(style: TextButton.styleFrom(foregroundColor: AppColors.ink)),
    // Picked sizes, fits and the in/cm switch: black (white in dark) like the website.
    segmentedButtonTheme: SegmentedButtonThemeData(
      style: ButtonStyle(
        backgroundColor: WidgetStateProperty.resolveWith(
          (s) => s.contains(WidgetState.selected) ? AppColors.ink : AppColors.card,
        ),
        foregroundColor: WidgetStateProperty.resolveWith(
          (s) => s.contains(WidgetState.selected) ? AppColors.onInk : AppColors.ink,
        ),
      ),
    ),
    chipTheme: ChipThemeData(
      backgroundColor: AppColors.card,
      selectedColor: AppColors.ink,
      secondarySelectedColor: AppColors.ink,
      side: BorderSide(color: AppColors.line),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      labelStyle: TextStyle(color: AppColors.ink, fontWeight: FontWeight.w500),
      secondaryLabelStyle: TextStyle(color: AppColors.onInk, fontWeight: FontWeight.w600),
    ),
    inputDecorationTheme: InputDecorationTheme(fillColor: AppColors.card),
    progressIndicatorTheme: ProgressIndicatorThemeData(color: AppColors.ink),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: AppColors.card,
      indicatorColor: AppColors.ink,
      elevation: 0,
      iconTheme: WidgetStateProperty.resolveWith(
        (states) => IconThemeData(color: states.contains(WidgetState.selected) ? AppColors.onInk : AppColors.muted),
      ),
      labelTextStyle: WidgetStateProperty.resolveWith(
        (states) => TextStyle(
          fontSize: 12,
          fontWeight: states.contains(WidgetState.selected) ? FontWeight.w600 : FontWeight.w500,
          color: states.contains(WidgetState.selected) ? AppColors.ink : AppColors.muted,
        ),
      ),
    ),
  );
}
