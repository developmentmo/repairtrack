import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/repair_api.dart';
import '../domain/repair.dart';

final vehicleRepairsProvider = FutureProvider.autoDispose.family<List<Repair>, String>(
  (ref, vehicleId) => ref.watch(repairApiProvider).history(vehicleId),
);

final mileageHistoryProvider = FutureProvider.autoDispose.family<MileageHistory, String>(
  (ref, vehicleId) => ref.watch(repairApiProvider).mileage(vehicleId),
);

final repairProvider = FutureProvider.autoDispose.family<Repair, String>(
  (ref, repairId) => ref.watch(repairApiProvider).repair(repairId),
);
