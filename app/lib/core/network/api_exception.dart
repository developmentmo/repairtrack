import 'package:dio/dio.dart';

/// An error returned by the backend (`{status, code, message}`) or a transport failure.
///
/// Code branches on [code], never on [message] (the backend message is English and meant for logs).
class ApiException implements Exception {
  const ApiException({required this.code, required this.message, this.statusCode});

  /// No response from the server (offline, timeout, DNS, ...).
  static const network = 'NETWORK_ERROR';

  /// A response without the backend's error body.
  static const unknown = 'UNKNOWN_ERROR';

  final int? statusCode;
  final String code;
  final String message;

  bool get isUnauthorized => statusCode == 401;

  bool get isNetworkError => code == network;

  factory ApiException.fromDio(DioException exception) {
    final cause = exception.error;
    if (cause is ApiException) {
      return cause;
    }
    final response = exception.response;
    if (response == null) {
      return const ApiException(code: network, message: 'No response from the server.');
    }
    final body = response.data;
    if (body is Map && body['code'] is String) {
      final message = body['message'];
      return ApiException(
        statusCode: response.statusCode,
        code: body['code'] as String,
        message: message is String ? message : '',
      );
    }
    return ApiException(
      statusCode: response.statusCode,
      code: unknown,
      message: 'HTTP ${response.statusCode}',
    );
  }

  @override
  String toString() => 'ApiException($statusCode, $code)';
}

/// Runs an API call and converts Dio errors into [ApiException].
Future<T> guardApi<T>(Future<T> Function() call) async {
  try {
    return await call();
  } on DioException catch (e) {
    throw ApiException.fromDio(e);
  }
}
