import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/async_value_view.dart';
import '../../../core/widgets/form_widgets.dart';
import '../application/vehicle_providers.dart';
import '../data/vehicle_api.dart';
import '../domain/vehicle.dart';

/// Vehicle details for the owner, or for a garage member when [garageId] is set.
class VehicleDetailScreen extends ConsumerWidget {
  const VehicleDetailScreen({super.key, required this.vehicleId, this.garageId});

  final String vehicleId;

  /// Set when opened from a garage dashboard: actions are taken on behalf of that garage.
  final String? garageId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final vehicle = ref.watch(vehicleProvider(vehicleId));
    return Scaffold(
      appBar: AppBar(title: Text(vehicle.hasValue ? vehicle.requireValue.displayName : 'Voertuig')),
      body: AsyncValueView(
        value: vehicle,
        onRetry: () => ref.invalidate(vehicleProvider(vehicleId)),
        data: (vehicle) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(vehicle.displayName, style: Theme.of(context).textTheme.titleLarge),
                    const SizedBox(height: 8),
                    DetailRow(label: 'Kenteken', value: vehicle.licensePlate ?? '-'),
                    DetailRow(label: 'Bouwjaar', value: vehicle.modelYear?.toString() ?? '-'),
                    DetailRow(
                      label: 'Eerste toelating',
                      value: vehicle.firstRegistrationDate == null ? '-' : formatDate(vehicle.firstRegistrationDate!),
                    ),
                    if (vehicle.vin != null) DetailRow(label: 'VIN', value: vehicle.vin!),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: () => context.go(Routes.vehicleHistoryFor(garageId, vehicle.id)),
              icon: const Icon(Icons.history),
              label: const Text('Onderhoudshistorie'),
            ),
            if (garageId != null) ...[
              const SizedBox(height: 8),
              OutlinedButton.icon(
                onPressed: () => context.go(Routes.garageNewRepair(garageId!, vehicle.id)),
                icon: const Icon(Icons.build_outlined),
                label: const Text('Werk vastleggen'),
              ),
            ],
            if (garageId == null && vehicle.ownedByMe) ...[
              const SizedBox(height: 8),
              OutlinedButton.icon(
                onPressed: () => context.go(Routes.newRepair(vehicle.id)),
                icon: const Icon(Icons.add),
                label: const Text('Onderhoud of reparatie toevoegen'),
              ),
              const SizedBox(height: 8),
              OutlinedButton.icon(
                onPressed: () => context.go(Routes.shareVehicle(vehicle.id)),
                icon: const Icon(Icons.share_outlined),
                label: const Text('Historie delen'),
              ),
              const SizedBox(height: 32),
              TextButton.icon(
                onPressed: () => _endOwnership(context, ref, vehicle),
                icon: const Icon(Icons.sell_outlined),
                label: const Text('Ik heb dit voertuig verkocht'),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _endOwnership(BuildContext context, WidgetRef ref, Vehicle vehicle) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Voertuig verkocht?'),
        content: const Text(
          'Je eigendom eindigt vandaag. De onderhoudshistorie blijft bij het voertuig, zodat de nieuwe eigenaar '
          'die kan inzien. Jij hebt daarna geen toegang meer.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Annuleren')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Eigendom beëindigen')),
        ],
      ),
    );
    if (confirmed != true) {
      return;
    }
    try {
      await ref.read(vehicleApiProvider).endOwnership(vehicle.id);
      ref.invalidate(myVehiclesProvider);
      if (context.mounted) {
        context.go(Routes.home);
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(userMessage(e))));
      }
    }
  }
}
