import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/share_api.dart';
import '../domain/public_report.dart';
import '../domain/share.dart';

final vehicleSharesProvider = FutureProvider.autoDispose.family<List<VehicleShare>, String>(
  (ref, vehicleId) => ref.watch(shareApiProvider).forVehicle(vehicleId),
);

final publicReportProvider = FutureProvider.autoDispose.family<PublicReport, String>(
  (ref, token) => ref.watch(publicReportApiProvider).report(token),
);
