import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:repairtrack_app/core/network/api_exception.dart';
import 'package:repairtrack_app/core/network/error_messages.dart';

void main() {
  final request = RequestOptions(path: '/api/v1/vehicles');

  test('reads code and message from the backend error body', () {
    final exception = ApiException.fromDio(
      DioException(
        requestOptions: request,
        response: Response<dynamic>(
          requestOptions: request,
          statusCode: 409,
          data: {'status': 409, 'code': 'VEHICLE_ALREADY_REGISTERED', 'message': 'exists'},
        ),
      ),
    );

    expect(exception.statusCode, 409);
    expect(exception.code, 'VEHICLE_ALREADY_REGISTERED');
    expect(exception.message, 'exists');
  });

  test('no response is a network error', () {
    final exception = ApiException.fromDio(
      DioException(requestOptions: request, type: DioExceptionType.connectionError),
    );

    expect(exception.isNetworkError, isTrue);
    expect(userMessage(exception), contains('Geen verbinding'));
  });

  test('a response without the error body keeps the status', () {
    final exception = ApiException.fromDio(
      DioException(
        requestOptions: request,
        response: Response<dynamic>(requestOptions: request, statusCode: 502, data: '<html>'),
      ),
    );

    expect(exception.statusCode, 502);
    expect(exception.code, ApiException.unknown);
  });

  test('user messages branch on the code, with a fallback for unknown codes', () {
    expect(userMessage(const ApiException(code: 'INVALID_CREDENTIALS', message: '')), 'E-mailadres of wachtwoord is onjuist.');
    expect(userMessage(const ApiException(code: 'SOMETHING_NEW', message: '')), contains('SOMETHING_NEW'));
  });
}
