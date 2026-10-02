import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/network/dio_provider.dart';
import '../domain/repair.dart';

final repairApiProvider = Provider<RepairApi>((ref) => RepairApi(ref.watch(dioProvider)));

class RepairApi {
  RepairApi(this._dio);

  final Dio _dio;

  /// Newest first, including voided records.
  Future<List<Repair>> history(String vehicleId) => guardApi(() async {
        final response = await _dio.get<List<dynamic>>('/api/v1/vehicles/$vehicleId/repairs');
        return response.data!.map((json) => Repair.fromJson(json as Map<String, dynamic>)).toList();
      });

  Future<MileageHistory> mileage(String vehicleId) => guardApi(() async {
        final response = await _dio.get<Map<String, dynamic>>('/api/v1/vehicles/$vehicleId/mileage');
        return MileageHistory.fromJson(response.data!);
      });

  Future<Repair> repair(String repairId) => guardApi(() async {
        final response = await _dio.get<Map<String, dynamic>>('/api/v1/repairs/$repairId');
        return Repair.fromJson(response.data!);
      });

  /// The response carries mileage `warnings`; they never block the save.
  Future<Repair> create(String vehicleId, NewRepair repair) => guardApi(() async {
        final response = await _dio.post<Map<String, dynamic>>(
          '/api/v1/vehicles/$vehicleId/repairs',
          data: repair.toJson(),
        );
        return Repair.fromJson(response.data!);
      });
}
