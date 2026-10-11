import 'package:flutter/material.dart';
import 'package:repairtrack_app/core/l10n/l10n.dart';

/// A MaterialApp with the app's localizations, in Dutch unless a test asks for another language.
class TestApp extends StatelessWidget {
  const TestApp({super.key, required this.home, this.locale = const Locale('nl')});

  final Widget home;
  final Locale locale;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      locale: locale,
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      home: home,
    );
  }
}
