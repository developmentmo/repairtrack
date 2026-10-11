import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/intl.dart';
import 'package:repairtrack_app/core/format/formatters.dart';
import 'package:repairtrack_app/core/l10n/l10n.dart';
import 'package:repairtrack_app/core/l10n/locale_controller.dart';
import 'package:repairtrack_app/core/network/dio_provider.dart';
import 'package:repairtrack_app/features/authentication/presentation/account_screen.dart';

import '../helpers/in_memory_language_store.dart';
import '../helpers/in_memory_token_store.dart';

void main() {
  group('device language', () {
    test('uses English on an English device', () {
      expect(AppLanguage.forDevice(const [Locale('en', 'GB')]), AppLanguage.english);
    });

    test('uses Dutch on a Dutch device', () {
      expect(AppLanguage.forDevice(const [Locale('nl', 'BE'), Locale('en')]), AppLanguage.dutch);
    });

    test('falls back to Dutch for languages the app does not have', () {
      expect(AppLanguage.forDevice(const [Locale('de'), Locale('fr')]), AppLanguage.dutch);
      expect(AppLanguage.forDevice(const []), AppLanguage.dutch);
    });

    test('takes the first supported language from the device list', () {
      expect(AppLanguage.forDevice(const [Locale('de'), Locale('en'), Locale('nl')]), AppLanguage.english);
    });
  });

  group('choice on the account page', () {
    late InMemoryLanguageStore store;

    setUp(() => store = InMemoryLanguageStore());

    Future<void> pumpAccount(WidgetTester tester, {AppLanguage? initial}) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            tokenStoreProvider.overrideWithValue(InMemoryTokenStore()),
            languageStoreProvider.overrideWithValue(store),
            initialLanguageProvider.overrideWithValue(initial),
          ],
          child: Consumer(
            builder: (context, ref, _) => MaterialApp(
              locale: ref.watch(shownLanguageProvider).locale,
              supportedLocales: AppLocalizations.supportedLocales,
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              home: const AccountScreen(),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('switches the app to English and remembers it', (tester) async {
      await pumpAccount(tester, initial: AppLanguage.dutch);
      expect(find.text('Account verwijderen'), findsWidgets);

      await tester.tap(find.text('English'));
      await tester.pumpAndSettle();

      expect(find.text('Delete account'), findsWidgets);
      expect(find.text('Language'), findsOneWidget);
      expect(store.language, AppLanguage.english);
    });

    testWidgets('switches back to Dutch', (tester) async {
      await pumpAccount(tester, initial: AppLanguage.english);
      expect(find.text('Delete account'), findsWidgets);

      await tester.tap(find.text('Nederlands'));
      await tester.pumpAndSettle();

      expect(find.text('Account verwijderen'), findsWidgets);
      expect(store.language, AppLanguage.dutch);
    });

    testWidgets('follows the device language until the user chooses', (tester) async {
      tester.platformDispatcher.localesTestValue = const [Locale('en', 'US')];
      addTearDown(tester.platformDispatcher.clearLocalesTestValue);
      await pumpAccount(tester);

      expect(find.text('Delete account'), findsWidgets);
      expect(store.language, isNull);
    });
  });

  group('number formatting', () {
    tearDown(() => Intl.defaultLocale = null);

    test('uses a dot as thousands separator in Dutch', () {
      applyFormattingLanguage(AppLanguage.dutch);
      expect(formatKm(183421), '183.421 km');
    });

    test('uses a comma as thousands separator in English', () {
      applyFormattingLanguage(AppLanguage.english);
      expect(formatKm(183421), '183,421 km');
    });
  });

  test('every Dutch text has an English translation', () {
    Map<String, dynamic> messages(String file) => Map.fromEntries(
          (jsonDecode(File('lib/l10n/$file').readAsStringSync()) as Map<String, dynamic>)
              .entries
              .where((e) => !e.key.startsWith('@')),
        );
    final dutch = messages('app_nl.arb');
    final english = messages('app_en.arb');

    expect(english.keys.toSet(), dutch.keys.toSet());
    for (final key in dutch.keys) {
      expect((english[key] as String).trim(), isNotEmpty, reason: key);
    }
  });
}
