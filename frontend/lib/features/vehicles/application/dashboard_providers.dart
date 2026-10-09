import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../repairs/data/repair_api.dart';
import '../../repairs/domain/repair.dart';
import '../domain/vehicle.dart';
import 'vehicle_providers.dart';

/// A repair together with the vehicle it belongs to, for the dashboard.
class VehicleRepair {
  const VehicleRepair({required this.vehicle, required this.repair});

  final Vehicle vehicle;
  final Repair repair;
}

/// The history of every vehicle the user owns, newest first. Voided records are left out.
/// A vehicle whose history fails to load is skipped, so one failure doesn't hide the whole dashboard.
final dashboardRepairsProvider = FutureProvider.autoDispose<List<VehicleRepair>>((ref) async {
  final vehicles = await ref.watch(myVehiclesProvider.future);
  final api = ref.watch(repairApiProvider);
  final histories = await Future.wait(
    vehicles.map((vehicle) async {
      try {
        final repairs = await api.history(vehicle.id);
        return [
          for (final repair in repairs)
            if (!repair.isVoided) VehicleRepair(vehicle: vehicle, repair: repair),
        ];
      } catch (_) {
        return const <VehicleRepair>[];
      }
    }),
  );
  return histories.expand((list) => list).toList()
    ..sort((a, b) => b.repair.eventDate.compareTo(a.repair.eventDate));
});
