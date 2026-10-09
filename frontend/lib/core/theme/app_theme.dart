import 'package:flutter/material.dart';

/// RepairTrack brand colours, taken from the logo (navy car, blue "Track").
class BrandColors {
  const BrandColors._();

  static const navy = Color(0xFF002246);
  static const blue = Color(0xFF0070FE);

  static const background = Color(0xFFF5F7FB);
  static const border = Color(0xFFE4E8F0);
  static const mutedText = Color(0xFF64748B);

  static const darkBackground = Color(0xFF0B1220);
  static const darkSurface = Color(0xFF131C2E);
  static const darkBorder = Color(0xFF243049);
}

class AppTheme {
  const AppTheme._();

  static ThemeData light() => _build(Brightness.light);

  static ThemeData dark() => _build(Brightness.dark);

  static ThemeData _build(Brightness brightness) {
    final dark = brightness == Brightness.dark;
    final background = dark ? BrandColors.darkBackground : BrandColors.background;
    final surface = dark ? BrandColors.darkSurface : Colors.white;
    final border = dark ? BrandColors.darkBorder : BrandColors.border;
    final scheme = ColorScheme.fromSeed(
      seedColor: BrandColors.blue,
      brightness: brightness,
      primary: BrandColors.blue,
      onPrimary: Colors.white,
      surface: surface,
      outlineVariant: border,
    );
    final base = ThemeData(colorScheme: scheme, useMaterial3: true, brightness: brightness);
    final textTheme = base.textTheme.apply(
      bodyColor: dark ? Colors.white : BrandColors.navy,
      displayColor: dark ? Colors.white : BrandColors.navy,
    );
    final rounded = RoundedRectangleBorder(borderRadius: BorderRadius.circular(10));
    const buttonPadding = EdgeInsets.symmetric(horizontal: 20, vertical: 16);
    return base.copyWith(
      scaffoldBackgroundColor: background,
      textTheme: textTheme.copyWith(
        headlineSmall: textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700),
        titleLarge: textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700),
        titleMedium: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
        titleSmall: textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600),
        bodySmall: textTheme.bodySmall?.copyWith(color: dark ? Colors.white70 : BrandColors.mutedText),
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: background,
        foregroundColor: dark ? Colors.white : BrandColors.navy,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        titleTextStyle: textTheme.titleLarge?.copyWith(fontSize: 20, fontWeight: FontWeight.w700),
      ),
      cardTheme: CardThemeData(
        color: surface,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        margin: const EdgeInsets.symmetric(vertical: 6),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
          side: BorderSide(color: border),
        ),
      ),
      dividerTheme: DividerThemeData(color: border, space: 1),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(shape: rounded, padding: buttonPadding),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(shape: rounded, padding: buttonPadding, side: BorderSide(color: border)),
      ),
      textButtonTheme: TextButtonThemeData(style: TextButton.styleFrom(shape: rounded)),
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        backgroundColor: BrandColors.blue,
        foregroundColor: Colors.white,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surface,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide(color: border)),
        enabledBorder:
            OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide(color: border)),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: BrandColors.blue, width: 1.5),
        ),
      ),
      listTileTheme: const ListTileThemeData(shape: RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(14)))),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: surface,
        surfaceTintColor: Colors.transparent,
        indicatorColor: scheme.primaryContainer,
        labelTextStyle: WidgetStatePropertyAll(textTheme.labelSmall),
      ),
      tabBarTheme: const TabBarThemeData(dividerColor: Colors.transparent),
      popupMenuTheme: PopupMenuThemeData(
        color: surface,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12), side: BorderSide(color: border)),
      ),
      dialogTheme: DialogThemeData(backgroundColor: surface, surfaceTintColor: Colors.transparent),
      snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating),
    );
  }
}
