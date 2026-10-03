import 'package:dio/dio.dart';

import '../auth/auth_tokens.dart';
import '../auth/token_store.dart';

/// Adds the access token to requests and, on a 401, refreshes the token pair once and retries.
///
/// It is a [QueuedInterceptor]: errors are handled one at a time, so parallel requests that all get a
/// 401 cause a single refresh (refresh tokens are single-use; reusing one revokes the whole session).
class AuthInterceptor extends QueuedInterceptor {
  AuthInterceptor({
    required this.tokenStore,
    required this.refreshDio,
    required this.onSessionExpired,
  });

  /// Set in [RequestOptions.extra] for endpoints that must not carry a token (login, register, ...).
  static const skipAuth = 'auth.skip';
  static const _retried = 'auth.retried';
  static const refreshPath = '/api/v1/auth/refresh';

  final TokenStore tokenStore;

  /// A Dio without this interceptor, used for the refresh call and the retry.
  final Dio refreshDio;

  /// Called when the refresh token is rejected: the user has to log in again.
  final void Function() onSessionExpired;

  @override
  Future<void> onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    if (options.extra[skipAuth] != true) {
      final tokens = await tokenStore.read();
      if (tokens != null) {
        options.headers['Authorization'] = 'Bearer ${tokens.accessToken}';
      }
    }
    handler.next(options);
  }

  @override
  Future<void> onError(DioException err, ErrorInterceptorHandler handler) async {
    final request = err.requestOptions;
    if (err.response?.statusCode != 401 || request.extra[skipAuth] == true || request.extra[_retried] == true) {
      handler.next(err);
      return;
    }
    final current = await tokenStore.read();
    if (current == null) {
      handler.next(err);
      return;
    }

    AuthTokens fresh;
    if (request.headers['Authorization'] != 'Bearer ${current.accessToken}') {
      // Another request refreshed the tokens while this one was in flight.
      fresh = current;
    } else {
      try {
        final response = await refreshDio.post<Map<String, dynamic>>(
          refreshPath,
          data: {'refreshToken': current.refreshToken},
        );
        fresh = AuthTokens.fromJson(response.data!);
        await tokenStore.write(fresh);
      } on DioException catch (refreshError) {
        final status = refreshError.response?.statusCode;
        if (status == 401 || status == 403) {
          await tokenStore.clear();
          onSessionExpired();
        }
        handler.next(err);
        return;
      }
    }

    try {
      final data = request.data;
      final retry = request.copyWith(
        headers: {...request.headers, 'Authorization': 'Bearer ${fresh.accessToken}'},
        extra: {...request.extra, _retried: true},
        // A multipart body is consumed by the first attempt; send a fresh copy.
        data: data is FormData ? data.clone() : data,
      );
      handler.resolve(await refreshDio.fetch<dynamic>(retry));
    } on DioException catch (retryError) {
      handler.next(retryError);
    }
  }
}
