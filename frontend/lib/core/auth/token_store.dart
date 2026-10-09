import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'auth_tokens.dart';

abstract interface class TokenStore {
  Future<AuthTokens?> read();

  Future<void> write(AuthTokens tokens);

  Future<void> clear();
}

/// Keychain (iOS) / Keystore-encrypted storage (Android). Cached in memory so requests don't hit the
/// platform channel every time.
class SecureTokenStore implements TokenStore {
  SecureTokenStore([FlutterSecureStorage? storage]) : _storage = storage ?? FlutterSecureStorage();

  static const _accessKey = 'auth.accessToken';
  static const _refreshKey = 'auth.refreshToken';

  final FlutterSecureStorage _storage;
  AuthTokens? _cache;
  bool _loaded = false;

  @override
  Future<AuthTokens?> read() async {
    if (_loaded) {
      return _cache;
    }
    final access = await _storage.read(key: _accessKey);
    final refresh = await _storage.read(key: _refreshKey);
    _cache = access != null && refresh != null ? AuthTokens(accessToken: access, refreshToken: refresh) : null;
    _loaded = true;
    return _cache;
  }

  @override
  Future<void> write(AuthTokens tokens) async {
    _cache = tokens;
    _loaded = true;
    await _storage.write(key: _accessKey, value: tokens.accessToken);
    await _storage.write(key: _refreshKey, value: tokens.refreshToken);
  }

  @override
  Future<void> clear() async {
    _cache = null;
    _loaded = true;
    await _storage.delete(key: _accessKey);
    await _storage.delete(key: _refreshKey);
  }
}
