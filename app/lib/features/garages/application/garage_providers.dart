import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../vehicles/domain/vehicle.dart';
import '../data/garage_api.dart';
import '../domain/garage.dart';

final myGaragesProvider = FutureProvider.autoDispose<List<MyGarage>>((ref) => ref.watch(garageApiProvider).mine());

final garageProvider = FutureProvider.autoDispose.family<Garage, String>(
  (ref, garageId) => ref.watch(garageApiProvider).garage(garageId),
);

final garageVehiclesProvider = FutureProvider.autoDispose.family<List<VehicleSearchResult>, String>(
  (ref, garageId) => ref.watch(garageApiProvider).vehicles(garageId),
);
