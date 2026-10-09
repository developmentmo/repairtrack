import 'package:repairtrack_app/core/auth/auth_tokens.dart';
import 'package:repairtrack_app/core/auth/token_store.dart';

class InMemoryTokenStore implements TokenStore {
  InMemoryTokenStore([this.tokens]);

  AuthTokens? tokens;

  @override
  Future<AuthTokens?> read() async => tokens;

  @override
  Future<void> write(AuthTokens tokens) async => this.tokens = tokens;

  @override
  Future<void> clear() async => tokens = null;
}
