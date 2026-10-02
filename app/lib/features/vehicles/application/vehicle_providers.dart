import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/vehicle_api.dart';
import '../domain/vehicle.dart';

/// autoDispose: data is dropped when no screen shows it, so nothing survives a logout.
final myVehiclesProvider = FutureProvider.autoDispose<List<Vehicle>>(
  (ref) => ref.watch(vehicleApiProvider).myVehicles(),
);

final vehicleProvider = FutureProvider.autoDispose.family<Vehicle, String>(
  (ref, vehicleId) => ref.watch(vehicleApiProvider).vehicle(vehicleId),
);
