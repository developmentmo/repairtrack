import 'package:flutter/foundation.dart';

/// Runtime configuration.
///
/// The backend URL can be set at build time:
/// `flutter run --dart-define=API_BASE_URL=https://api.example.com`
/// or, for the deployed web app, `--dart-define=API_BASE_URL=same-origin`.
class AppConfig {
  const AppConfig._();

  static const _apiBaseUrlOverride = String.fromEnvironment('API_BASE_URL');

  /// Public website (privacy policy, account page). Override with `--dart-define=PUBLIC_WEB_URL=...`.
  static const _publicWebUrl = String.fromEnvironment('PUBLIC_WEB_URL', defaultValue: 'https://repairtrack.nl');

  /// The privacy policy: on the web relative to the host the app runs on (so staging links to staging).
  static Uri get privacyPolicyUrl => Uri.parse('${kIsWeb ? Uri.base.origin : _publicWebUrl}/privacy');

  static String get apiBaseUrl {
    // Deployed web app: the reverse proxy serves the app and /api on the same host, so one build works for
    // staging and production (`--dart-define=API_BASE_URL=same-origin`).
    if (_apiBaseUrlOverride == 'same-origin' && kIsWeb) {
      return Uri.base.origin;
    }
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
