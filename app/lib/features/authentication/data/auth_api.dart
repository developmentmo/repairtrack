import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/auth_tokens.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/network/auth_interceptor.dart';
import '../../../core/network/dio_provider.dart';
import '../domain/user.dart';

final authApiProvider = Provider<AuthApi>((ref) => AuthApi(ref.watch(dioProvider)));

class AuthApi {
  AuthApi(this._dio);

  final Dio _dio;

  Options _public() => Options(extra: {AuthInterceptor.skipAuth: true});

  Future<AuthTokens> login({required String email, required String password}) => guardApi(() async {
        final response = await _dio.post<Map<String, dynamic>>(
          '/api/v1/auth/login',
          data: {'email': email, 'password': password},
          options: _public(),
        );
        return AuthTokens.fromJson(response.data!);
      });

  Future<void> register({
    required String email,
    required String password,
    required String firstName,
    required String lastName,
  }) =>
      guardApi(() async {
        await _dio.post<void>(
          '/api/v1/auth/register',
          data: {'email': email, 'password': password, 'firstName': firstName, 'lastName': lastName},
          options: _public(),
        );
      });

  Future<void> logout(String refreshToken) => guardApi(() async {
        await _dio.post<void>('/api/v1/auth/logout', data: {'refreshToken': refreshToken}, options: _public());
      });

  Future<User> me() => guardApi(() async {
        final response = await _dio.get<Map<String, dynamic>>('/api/v1/users/me');
        return User.fromJson(response.data!);
      });
}
