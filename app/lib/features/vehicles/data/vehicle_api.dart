import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/network/dio_provider.dart';
import '../domain/vehicle.dart';

final vehicleApiProvider = Provider<VehicleApi>((ref) => VehicleApi(ref.watch(dioProvider)));

class VehicleApi {
  VehicleApi(this._dio);

  final Dio _dio;

  /// Vehicles the caller currently owns.
  Future<List<Vehicle>> myVehicles() => guardApi(() async {
        final response = await _dio.get<List<dynamic>>('/api/v1/vehicles');
        return response.data!.map((json) => Vehicle.fromJson(json as Map<String, dynamic>)).toList();
      });

  Future<Vehicle> vehicle(String vehicleId) => guardApi(() async {
        final response = await _dio.get<Map<String, dynamic>>('/api/v1/vehicles/$vehicleId');
        return Vehicle.fromJson(response.data!);
      });

  Future<Vehicle> register(NewVehicle vehicle) => guardApi(() async {
        final response = await _dio.post<Map<String, dynamic>>('/api/v1/vehicles', data: vehicle.toJson());
        return Vehicle.fromJson(response.data!);
      });

  Future<List<VehicleSearchResult>> searchByLicensePlate(String licensePlate) => guardApi(() async {
        final response = await _dio.get<List<dynamic>>(
          '/api/v1/vehicles/search',
          queryParameters: {'licensePlate': licensePlate},
        );
        return response.data!.map((json) => VehicleSearchResult.fromJson(json as Map<String, dynamic>)).toList();
      });

  /// Public RDW data for a plate (404 REGISTRY_VEHICLE_NOT_FOUND, 503 REGISTRY_UNAVAILABLE).
  Future<RegistryVehicle> registryLookup(String licensePlate) => guardApi(() async {
        final plate = licensePlate.replaceAll(RegExp(r'[\s-]'), '').toUpperCase();
        final response = await _dio.get<Map<String, dynamic>>(
          '/api/v1/vehicle-registry/${Uri.encodeComponent(plate)}',
        );
        return RegistryVehicle.fromJson(response.data!);
      });

  /// Take ownership of an unowned vehicle; the full VIN is the proof.
  Future<void> claim(String vehicleId, {required String vin, DateTime? ownedSince}) => guardApi(() async {
        await _dio.post<void>(
          '/api/v1/vehicles/$vehicleId/claim',
          data: {'vin': vin, if (ownedSince != null) 'ownedSince': toWireDate(ownedSince)},
        );
      });

  /// "I sold this vehicle": ends the caller's ownership today. The history stays with the vehicle.
  Future<void> endOwnership(String vehicleId) => guardApi(() async {
        await _dio.post<void>('/api/v1/vehicles/$vehicleId/ownership/end');
      });
}
