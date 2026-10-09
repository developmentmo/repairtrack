import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../data/vehicle_api.dart';
import '../domain/vehicle.dart';
import '../domain/vehicle_photo.dart';

/// autoDispose: data is dropped when no screen shows it, so nothing survives a logout.
final myVehiclesProvider = FutureProvider.autoDispose<List<Vehicle>>(
  (ref) => ref.watch(vehicleApiProvider).myVehicles(),
);

final vehicleProvider = FutureProvider.autoDispose.family<Vehicle, String>(
  (ref, vehicleId) => ref.watch(vehicleApiProvider).vehicle(vehicleId),
);

/// The owner's vehicle photo, null when there is none. Each read gets a fresh short-lived download URL.
final vehiclePhotoProvider = FutureProvider.autoDispose.family<VehiclePhoto?, String>(
  (ref, vehicleId) => ref.watch(vehicleApiProvider).photo(vehicleId),
);

/// Gallery and camera access; overridden in tests.
final imagePickerProvider = Provider<ImagePicker>((ref) => ImagePicker());
