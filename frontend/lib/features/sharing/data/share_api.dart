import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/network/auth_interceptor.dart';
import '../../../core/network/dio_provider.dart';
import '../domain/public_report.dart';
import '../domain/share.dart';

final shareApiProvider = Provider<ShareApi>((ref) => ShareApi(ref.watch(dioProvider)));

/// Owner side: create, list and revoke share links.
class ShareApi {
  ShareApi(this._dio);

  final Dio _dio;

  Future<CreatedShare> create(String vehicleId, {required int validDays, required bool includeDocuments}) =>
      guardApi(() async {
        final response = await _dio.post<Map<String, dynamic>>(
          '/api/v1/vehicles/$vehicleId/shares',
          data: {'validDays': validDays, 'includeDocuments': includeDocuments},
        );
        return CreatedShare.fromJson(response.data!);
      });

  /// The owner's own links for this vehicle, newest first.
  Future<List<VehicleShare>> forVehicle(String vehicleId) => guardApi(() async {
        final response = await _dio.get<List<dynamic>>('/api/v1/vehicles/$vehicleId/shares');
        return response.data!.map((json) => VehicleShare.fromJson(json as Map<String, dynamic>)).toList();
      });

  Future<VehicleShare> revoke(String shareId) => guardApi(() async {
        final response = await _dio.post<Map<String, dynamic>>('/api/v1/shares/$shareId/revoke');
        return VehicleShare.fromJson(response.data!);
      });
}

final publicReportApiProvider = Provider<PublicReportApi>((ref) => PublicReportApi(ref.watch(dioProvider)));

/// Public side: no login. Never sends the user's token, even if someone is logged in on this device.
class PublicReportApi {
  PublicReportApi(this._dio);

  final Dio _dio;

  Options _public() => Options(extra: {AuthInterceptor.skipAuth: true});

  Future<PublicReport> report(String token) => guardApi(() async {
        final response = await _dio.get<Map<String, dynamic>>(
          '/api/v1/public/vehicles/${Uri.encodeComponent(token)}',
          options: _public(),
        );
        return PublicReport.fromJson(response.data!);
      });

  Future<PublicDocumentLink> documentLink(String token, String reference) => guardApi(() async {
        final response = await _dio.get<Map<String, dynamic>>(
          '/api/v1/public/vehicles/${Uri.encodeComponent(token)}/documents/${Uri.encodeComponent(reference)}',
          options: _public(),
        );
        return PublicDocumentLink.fromJson(response.data!);
      });
}
