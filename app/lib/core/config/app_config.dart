import 'package:flutter/foundation.dart';

/// Runtime configuration.
///
/// The backend URL can be set at build time:
/// `flutter run --dart-define=API_BASE_URL=https://api.example.com`
class AppConfig {
  const AppConfig._();

  static const _apiBaseUrlOverride = String.fromEnvironment('API_BASE_URL');

  static String get apiBaseUrl {
    if (_apiBaseUrlOverride.isNotEmpty) {
      return _apiBaseUrlOverride;
    }
    // The Android emulator reaches the host machine through 10.0.2.2.
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      return 'http://10.0.2.2:8080';
    }
    return 'http://localhost:8080';
  }
}
