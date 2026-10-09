/// The token pair from `POST /auth/login` and `/auth/refresh`.
class AuthTokens {
  const AuthTokens({required this.accessToken, required this.refreshToken});

  factory AuthTokens.fromJson(Map<String, dynamic> json) => AuthTokens(
        accessToken: json['accessToken'] as String,
        refreshToken: json['refreshToken'] as String,
      );

  final String accessToken;

  /// Single-use: every refresh returns a new one, which must replace the old one.
  final String refreshToken;

  @override
  String toString() => 'AuthTokens(***)';
}
