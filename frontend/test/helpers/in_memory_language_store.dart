import 'package:repairtrack_app/core/l10n/locale_controller.dart';

class InMemoryLanguageStore implements LanguageStore {
  AppLanguage? language;

  @override
  Future<AppLanguage?> read() async => language;

  @override
  Future<void> write(AppLanguage language) async => this.language = language;
}
