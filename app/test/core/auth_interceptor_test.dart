import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:repairtrack_app/core/auth/auth_tokens.dart';
import 'package:repairtrack_app/core/network/auth_interceptor.dart';

import '../helpers/fake_http_adapter.dart';
import '../helpers/in_memory_token_store.dart';

void main() {
  late InMemoryTokenStore store;
  late Dio dio;
  late FakeHttpAdapter adapter;
  late int sessionExpiredCalls;
  late bool refreshSucceeds;

  setUp(() {
    store = InMemoryTokenStore(const AuthTokens(accessToken: 'old-access', refreshToken: 'refresh-1'));
    sessionExpiredCalls = 0;
    refreshSucceeds = true;
    adapter = FakeHttpAdapter((request) async {
      if (request.path == AuthInterceptor.refreshPath) {
        if (!refreshSucceeds) {
          return errorResponse(401, 'INVALID_REFRESH_TOKEN');
        }
        expect(request.data, {'refreshToken': 'refresh-1'});
        return jsonResponse({'accessToken': 'new-access', 'refreshToken': 'refresh-2'});
      }
      if (request.headers['Authorization'] == 'Bearer new-access') {
        return jsonResponse({'path': request.path});
      }
      return errorResponse(401, 'UNAUTHORIZED');
    });
    final refreshDio = Dio(BaseOptions(baseUrl: 'http://test'))..httpClientAdapter = adapter;
    dio = Dio(BaseOptions(baseUrl: 'http://test'))
      ..httpClientAdapter = adapter
      ..interceptors.add(
        AuthInterceptor(tokenStore: store, refreshDio: refreshDio, onSessionExpired: () => sessionExpiredCalls++),
      );
  });

  int refreshCalls() => adapter.requests.where((r) => r.path == AuthInterceptor.refreshPath).length;

  test('adds the access token to requests', () async {
    await dio.get<dynamic>('/api/v1/users/me');

    expect(adapter.requests.first.headers['Authorization'], 'Bearer old-access');
  });

  test('on 401 it refreshes once, stores the new pair and retries the request', () async {
    final response = await dio.get<Map<String, dynamic>>('/api/v1/users/me');

    expect(response.data, {'path': '/api/v1/users/me'});
    expect(refreshCalls(), 1);
    expect(store.tokens!.accessToken, 'new-access');
    expect(store.tokens!.refreshToken, 'refresh-2');
    expect(sessionExpiredCalls, 0);
  });

  test('parallel 401s cause a single refresh (refresh tokens are single-use)', () async {
    final responses = await Future.wait([
      dio.get<Map<String, dynamic>>('/api/v1/vehicles'),
      dio.get<Map<String, dynamic>>('/api/v1/users/me'),
      dio.get<Map<String, dynamic>>('/api/v1/garages/mine'),
    ]);

    expect(responses.map((r) => r.statusCode), [200, 200, 200]);
    expect(refreshCalls(), 1);
  });

  test('a rejected refresh token clears the session', () async {
    refreshSucceeds = false;

    await expectLater(
      dio.get<dynamic>('/api/v1/users/me'),
      throwsA(isA<DioException>().having((e) => e.response?.statusCode, 'status', 401)),
    );
    expect(store.tokens, isNull);
    expect(sessionExpiredCalls, 1);
  });

  test('public endpoints are sent without a token and never refreshed', () async {
    await expectLater(
      dio.post<dynamic>('/api/v1/auth/login', options: Options(extra: {AuthInterceptor.skipAuth: true})),
      throwsA(isA<DioException>()),
    );

    expect(adapter.requests.single.headers.containsKey('Authorization'), isFalse);
    expect(refreshCalls(), 0);
  });
}
