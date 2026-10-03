import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/async_value_view.dart';
import '../../../core/widgets/form_widgets.dart';
import '../../vehicles/application/vehicle_providers.dart';
import '../application/repair_providers.dart';
import '../domain/repair.dart';
import 'repair_labels.dart';

/// The full history of a vehicle, newest first. Voided records stay visible.
class VehicleHistoryScreen extends ConsumerWidget {
  const VehicleHistoryScreen({super.key, required this.vehicleId, this.garageId});

  final String vehicleId;

  /// Set when a garage member views the history on behalf of that garage.
  final String? garageId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
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
      appBar: AppBar(title: const Text('Onderhoudshistorie')),
      floatingActionButton: canAdd
          ? FloatingActionButton(
              tooltip: 'Toevoegen',
              onPressed: () => context.go(Routes.newRepairFor(garageId, vehicleId)),
              child: const Icon(Icons.add),
            )
          : null,
      body: RefreshIndicator(
        onRefresh: refresh,
        child: _garageCannotSeeHistoryYet(repairs)
            ? const Padding(
                padding: EdgeInsets.all(16),
                child: InfoBanner(
                  message: 'De historie van dit voertuig is zichtbaar voor je garage zodra je er werk aan hebt '
                      'vastgelegd of het hebt geregistreerd.',
                ),
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
                  InfoBanner(message: mileageWarningText(warning), icon: Icons.speed, warning: true),
                  const SizedBox(height: 8),
                ],
                if (list.isEmpty)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 48),
                    child: Text('Nog geen onderhoud of reparaties geregistreerd.', textAlign: TextAlign.center),
                  ),
                for (final repair in list) _RepairTile(repair: repair),
              ],
            );
          },
        ),
      ),
    );
  }

  bool _garageCannotSeeHistoryYet(AsyncValue<List<Repair>> repairs) {
    final error = repairs.error;
    return garageId != null && error is ApiException && error.code == 'REPAIR_ACCESS_DENIED';
  }
}

class _RepairTile extends StatelessWidget {
  const _RepairTile({required this.repair});

  final Repair repair;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final voided = repair.isVoided;
    final titleStyle = voided
        ? theme.textTheme.titleMedium?.copyWith(decoration: TextDecoration.lineThrough, color: theme.disabledColor)
        : theme.textTheme.titleMedium;
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => context.go(Routes.repair(repair.id)),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(child: Text(repair.title, style: titleStyle)),
                  if (voided)
                    Text('Ongeldig', style: TextStyle(color: theme.colorScheme.error))
                  else
                    VerificationBadge(status: repair.verificationStatus),
                ],
              ),
              const SizedBox(height: 4),
              Text(
                '${formatDate(repair.eventDate)} · ${formatKm(repair.mileage)} · ${eventTypeLabel(repair.eventType)}',
                style: theme.textTheme.bodySmall,
              ),
              const SizedBox(height: 2),
              Text(
                repair.garage?.name ?? sourceTypeLabel(repair.sourceType),
                style: theme.textTheme.bodySmall,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
