import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/l10n/l10n.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/async_value_view.dart';
import '../../../core/widgets/brand_widgets.dart';
import '../../../core/widgets/form_widgets.dart';
import '../../vehicles/application/vehicle_providers.dart';
import '../application/repair_providers.dart';
import '../domain/repair.dart';
import 'repair_labels.dart';
import 'repair_tiles.dart';

/// The full history of a vehicle, newest first. Voided records stay visible.
class VehicleHistoryScreen extends ConsumerWidget {
  const VehicleHistoryScreen({super.key, required this.vehicleId, this.garageId});

  final String vehicleId;

  /// Set when a garage member views the history on behalf of that garage.
  final String? garageId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = context.l10n;
    final repairs = ref.watch(vehicleRepairsProvider(vehicleId));
    final mileage = ref.watch(mileageHistoryProvider(vehicleId));
    final vehicle = ref.watch(vehicleProvider(vehicleId));
    final canAdd = garageId != null || (vehicle.hasValue && vehicle.requireValue.ownedByMe);

    Future<void> refresh() async {
      ref
        ..invalidate(mileageHistoryProvider(vehicleId))
        ..invalidate(vehicleRepairsProvider(vehicleId));
      await ref.read(vehicleRepairsProvider(vehicleId).future);
    }

    return Scaffold(
      appBar: AppBar(title: Text(l.vehicleHistoryTitle)),
      floatingActionButton: canAdd
          ? FloatingActionButton.extended(
              tooltip: l.commonAdd,
              onPressed: () => context.go(Routes.newRepairFor(garageId, vehicleId)),
              icon: const Icon(Icons.add),
              label: Text(l.vehicleHistoryNewRepair),
            )
          : null,
      body: ContentWidth(
        maxWidth: 900,
        child: RefreshIndicator(
        onRefresh: refresh,
        child: _garageCannotSeeHistoryYet(repairs)
            ? Padding(
                padding: const EdgeInsets.all(16),
                child: InfoBanner(message: l.vehicleHistoryGarageNoAccess),
              )
            : AsyncValueView(
          value: repairs,
          onRetry: () => ref.invalidate(vehicleRepairsProvider(vehicleId)),
          data: (list) {
            final warnings = mileage.hasValue ? mileage.requireValue.anomalies : const <MileageWarning>[];
            return ListView(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
              children: [
                for (final warning in warnings) ...[
                  InfoBanner(message: mileageWarningText(l, warning), icon: Icons.speed, warning: true),
                  const SizedBox(height: 8),
                ],
                if (list.isEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 48),
                    child: Text(l.vehicleHistoryEmpty, textAlign: TextAlign.center),
                  ),
                if (list.isNotEmpty)
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                      child: Column(
                        children: [
                          for (final (index, repair) in list.indexed)
                            RepairTimelineTile(repair: repair, isFirst: index == 0, isLast: index == list.length - 1),
                        ],
                      ),
                    ),
                  ),
              ],
            );
          },
        ),
        ),
      ),
    );
  }

  bool _garageCannotSeeHistoryYet(AsyncValue<List<Repair>> repairs) {
    final error = repairs.error;
    return garageId != null && error is ApiException && error.code == 'REPAIR_ACCESS_DENIED';
  }
}
