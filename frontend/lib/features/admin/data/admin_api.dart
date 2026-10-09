import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/network/dio_provider.dart';
import '../../garages/domain/garage.dart';
import '../domain/admin_user.dart';

final adminApiProvider = Provider<AdminApi>((ref) => AdminApi(ref.watch(dioProvider)));

/// SYSTEM_ADMIN endpoints. The backend checks the role on every call.
class AdminApi {
  AdminApi(this._dio);

  final Dio _dio;

  /// The verification queue, oldest first.
  Future<List<Garage>> pendingGarages() => guardApi(() async {
        final response = await _dio.get<List<dynamic>>(
          '/api/v1/garages',
          queryParameters: {'verificationStatus': 'PENDING'},
        );
        return response.data!.map((json) => Garage.fromJson(json as Map<String, dynamic>)).toList();
      });

  /// [status]: `VERIFIED`, `UNVERIFIED` or `SUSPENDED`.
  Future<void> decideGarage(String garageId, String status, {String? note}) => guardApi(() async {
        await _dio.post<void>(
          '/api/v1/garages/$garageId/verification',
          data: {'status': status, if (note != null && note.isNotEmpty) 'note': note},
        );
      });

  Future<AdminUser> findUser(String email) => guardApi(() async {
        final response = await _dio.get<Map<String, dynamic>>(
          '/api/v1/admin/users',
          queryParameters: {'email': email},
        );
        return AdminUser.fromJson(response.data!);
      });

  Future<AdminUser> block(String userId) => guardApi(() async {
        final response = await _dio.post<Map<String, dynamic>>('/api/v1/admin/users/$userId/block');
        return AdminUser.fromJson(response.data!);
      });

  Future<AdminUser> unblock(String userId) => guardApi(() async {
        final response = await _dio.post<Map<String, dynamic>>('/api/v1/admin/users/$userId/unblock');
        return AdminUser.fromJson(response.data!);
      });
}

final pendingGaragesProvider = FutureProvider.autoDispose<List<Garage>>(
  (ref) => ref.watch(adminApiProvider).pendingGarages(),
);
