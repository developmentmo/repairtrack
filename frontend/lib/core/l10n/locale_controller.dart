import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// The languages the app is available in. Dutch comes first: it is the default.
enum AppLanguage {
  dutch('nl'),
  english('en');

  const AppLanguage(this.code);

  final String code;

  Locale get locale => Locale(code);

  static AppLanguage? fromCode(String? code) => values.where((l) => l.code == code).firstOrNull;

  /// The device language when the app supports it, otherwise Dutch.
  static AppLanguage forDevice(Iterable<Locale> deviceLocales) {
    for (final locale in deviceLocales) {
      final language = fromCode(locale.languageCode);
      if (language != null) {
        return language;
      }
    }
    return dutch;
  }
}

/// Remembers the language the user picked, on this device only.
abstract interface class LanguageStore {
  Future<AppLanguage?> read();

  Future<void> write(AppLanguage language);
}

class SharedPreferencesLanguageStore implements LanguageStore {
  static const _key = 'app.language';

  final SharedPreferencesAsync _preferences = SharedPreferencesAsync();

  @override
  Future<AppLanguage?> read() async => AppLanguage.fromCode(await _preferences.getString(_key));

  @override
  Future<void> write(AppLanguage language) => _preferences.setString(_key, language.code);
}

final languageStoreProvider = Provider<LanguageStore>((ref) => SharedPreferencesLanguageStore());

/// The language the user picked on this device; null until they pick one, so the app follows the device language.
/// `main` overrides this with the stored choice before the first frame.
final initialLanguageProvider = Provider<AppLanguage?>((ref) => null);

final languageControllerProvider = NotifierProvider<LanguageController, AppLanguage?>(LanguageController.new);

class LanguageController extends Notifier<AppLanguage?> {
  @override
  AppLanguage? build() => ref.watch(initialLanguageProvider);

  Future<void> select(AppLanguage language) async {
    state = language;
    await ref.read(languageStoreProvider).write(language);
  }
}

/// The language the app is shown in: the user's choice, or until they make one, the device language (Dutch when
/// the device uses a language the app doesn't have).
final shownLanguageProvider = Provider<AppLanguage>(
  (ref) => ref.watch(languageControllerProvider) ?? AppLanguage.forDevice(WidgetsBinding.instance.platformDispatcher.locales),
);

/// Makes the formatters (thousands separators) follow the language that is shown.
void applyFormattingLanguage(AppLanguage language) => Intl.defaultLocale = language.code;
