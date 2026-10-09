import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/network/dio_provider.dart';
import '../../vehicles/domain/vehicle.dart';
import '../domain/garage.dart';

final garageApiProvider = Provider<GarageApi>((ref) => GarageApi(ref.watch(dioProvider)));

class GarageApi {
  GarageApi(this._dio);

  final Dio _dio;

  Future<List<MyGarage>> mine() => guardApi(() async {
        final response = await _dio.get<List<dynamic>>('/api/v1/garages/mine');
        return response.data!.map((json) => MyGarage.fromJson(json as Map<String, dynamic>)).toList();
      });

  Future<Garage> garage(String garageId) => guardApi(() async {
        final response = await _dio.get<Map<String, dynamic>>('/api/v1/garages/$garageId');
        return Garage.fromJson(response.data!);
      });

  Future<Garage> register(NewGarage garage) => guardApi(() async {
        final response = await _dio.post<Map<String, dynamic>>('/api/v1/garages', data: garage.toJson());
        return Garage.fromJson(response.data!);
      });

  /// Garage admin: UNVERIFIED → PENDING.
  Future<void> requestVerification(String garageId) => guardApi(() async {
        await _dio.post<void>('/api/v1/garages/$garageId/verification-request');
      });

  /// Vehicles the garage registered or recorded work on.
  Future<List<VehicleSearchResult>> vehicles(String garageId) => guardApi(() async {
        final response = await _dio.get<List<dynamic>>('/api/v1/garages/$garageId/vehicles');
        return response.data!.map((json) => VehicleSearchResult.fromJson(json as Map<String, dynamic>)).toList();
      });
}
