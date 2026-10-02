import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../auth/session_controller.dart';
import '../auth/token_store.dart';
import '../config/app_config.dart';
import 'auth_interceptor.dart';

final tokenStoreProvider = Provider<TokenStore>((ref) => SecureTokenStore());

BaseOptions _options() => BaseOptions(
      baseUrl: AppConfig.apiBaseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 30),
      contentType: Headers.jsonContentType,
      responseType: ResponseType.json,
    );

/// The Dio instance for all backend calls.
final dioProvider = Provider<Dio>((ref) {
  final refreshDio = Dio(_options());
  final dio = Dio(_options())
    ..interceptors.add(
      AuthInterceptor(
        tokenStore: ref.watch(tokenStoreProvider),
        refreshDio: refreshDio,
        // Read lazily: the session controller itself uses this Dio.
        onSessionExpired: () => ref.read(sessionControllerProvider.notifier).sessionExpired(),
      ),
    );
  ref.onDispose(() {
    dio.close();
    refreshDio.close();
  });
  return dio;
});
