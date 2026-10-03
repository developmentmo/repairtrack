import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/authentication/data/auth_api.dart';
import '../../features/authentication/domain/user.dart';
import '../network/api_exception.dart';
import '../network/dio_provider.dart';

sealed class SessionState {
  const SessionState();
}

/// App start: checking stored tokens.
final class SessionRestoring extends SessionState {
  const SessionRestoring();
}

/// Tokens are stored but the server could not be reached to confirm them.
final class SessionOffline extends SessionState {
  const SessionOffline(this.error);

  final Object error;
}

final class SignedOut extends SessionState {
  const SignedOut({this.expired = false});

  /// True when the session ended because the refresh token was rejected.
  final bool expired;
}

final class SignedIn extends SessionState {
  const SignedIn(this.user);

  final User user;
}

final sessionControllerProvider = NotifierProvider<SessionController, SessionState>(SessionController.new);

class SessionController extends Notifier<SessionState> {
  @override
  SessionState build() {
    Future.microtask(restore);
    return const SessionRestoring();
  }

  AuthApi get _api => ref.read(authApiProvider);

  Future<void> restore() async {
    state = const SessionRestoring();
    final tokens = await ref.read(tokenStoreProvider).read();
    if (tokens == null) {
      state = const SignedOut();
      return;
    }
    try {
      state = SignedIn(await _api.me());
    } on ApiException catch (e) {
      if (e.isUnauthorized) {
        await ref.read(tokenStoreProvider).clear();
        state = const SignedOut();
      } else {
        state = SessionOffline(e);
      }
    }
  }

  /// Throws [ApiException] on failure (e.g. `INVALID_CREDENTIALS`).
  Future<void> login({required String email, required String password}) async {
    final tokens = await _api.login(email: email, password: password);
    await ref.read(tokenStoreProvider).write(tokens);
    state = SignedIn(await _api.me());
  }

  /// Creates an account. Login is possible only after the emailed link is followed. Throws [ApiException].
  Future<void> register({
    required String email,
    required String password,
    required String firstName,
    required String lastName,
  }) async {
    await _api.register(email: email, password: password, firstName: firstName, lastName: lastName);
  }

  Future<void> logout() async {
    final store = ref.read(tokenStoreProvider);
    final tokens = await store.read();
    await store.clear();
    state = const SignedOut();
    if (tokens != null) {
      try {
        await _api.logout(tokens.refreshToken);
      } on ApiException {
        // Best effort: the tokens are already gone on this device.
      }
    }
  }

  /// Called by the network layer when the refresh token is rejected.
  void sessionExpired() {
    if (state is SignedIn || state is SessionRestoring) {
      state = const SignedOut(expired: true);
    }
  }
}
