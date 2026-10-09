import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/async_value_view.dart';
import '../../../core/widgets/brand_widgets.dart';
import '../../../core/widgets/form_widgets.dart';
import '../../disputes/application/dispute_providers.dart';
import '../../disputes/domain/dispute.dart';
import '../../repairs/application/repair_providers.dart';
import '../../repairs/presentation/repair_tiles.dart';
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
    final disputes = garageId == null
        ? ref.watch(ownershipDisputesProvider(vehicleId)).value ?? const <PartyDispute>[]
        : const <PartyDispute>[];
    return Scaffold(
      appBar: AppBar(title: Text(garageId == null ? 'Mijn voertuig' : 'Voertuig')),
      body: AsyncValueView(
        value: vehicle,
        onRetry: () => ref.invalidate(vehicleProvider(vehicleId)),
        data: (vehicle) => ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
          children: [
            if (disputes.isNotEmpty && vehicle.ownedByMe) ...[
              InfoBanner(
                key: const Key('ownership-disputed'),
                warning: true,
                icon: Icons.gavel,
                message: disputes.any((d) => d.canRespond)
                    ? 'Iemand betwist dat jij de eigenaar bent. Reageer via Geschillen en voeg bewijs toe.'
                    : 'Het eigendom van dit voertuig wordt beoordeeld. Tot er is beslist, kun je geen deellinks maken.',
              ),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(onPressed: () => context.go(Routes.disputes), child: const Text('Naar geschillen')),
              ),
              const SizedBox(height: 8),
            ],
            _VehicleHeader(vehicle: vehicle),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                if (garageId != null)
                  FilledButton.icon(
                    onPressed: () => context.go(Routes.garageNewRepair(garageId!, vehicle.id)),
                    icon: const Icon(Icons.build_outlined),
                    label: const Text('Werk vastleggen'),
                  ),
                if (garageId == null && vehicle.ownedByMe) ...[
                  FilledButton.icon(
                    onPressed: () => context.go(Routes.newRepair(vehicle.id)),
                    icon: const Icon(Icons.add),
                    label: const Text('Nieuwe reparatie'),
                  ),
                  OutlinedButton.icon(
                    onPressed: () => context.go(Routes.shareVehicle(vehicle.id)),
                    icon: const Icon(Icons.share_outlined),
                    label: const Text('Historie delen'),
                  ),
                ],
              ],
            ),
            const SizedBox(height: 12),
            _HistoryPreview(vehicleId: vehicle.id, garageId: garageId),
            SectionCard(
              title: 'Gegevens',
              child: Column(
                children: [
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
            if (garageId == null && vehicle.ownedByMe) ...[
              const SizedBox(height: 16),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                  onPressed: () => _endOwnership(context, ref, vehicle),
                  icon: const Icon(Icons.sell_outlined),
                  label: const Text('Ik heb dit voertuig verkocht'),
                ),
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

class _VehicleHeader extends StatelessWidget {
  const _VehicleHeader({required this.vehicle});

  final Vehicle vehicle;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            const VehiclePicture(width: 120, height: 84),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(vehicle.displayName, style: theme.textTheme.titleLarge),
                  if (vehicle.modelYear != null) Text('${vehicle.modelYear}', style: theme.textTheme.bodySmall),
                  if (vehicle.licensePlate != null) ...[
                    const SizedBox(height: 8),
                    LicensePlate(plate: vehicle.licensePlate!),
                  ],
                ],
              ),
            ),
            if (vehicle.ownedByMe)
              const StatusPill(label: 'Actief', tone: Tone.success, icon: Icons.check_circle_outline),
          ],
        ),
      ),
    );
  }
}

/// The latest records, with a link to the full history.
class _HistoryPreview extends ConsumerWidget {
  const _HistoryPreview({required this.vehicleId, this.garageId});

  final String vehicleId;
  final String? garageId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final repairs = ref.watch(vehicleRepairsProvider(vehicleId));
    final openHistory = SectionLink(
      label: 'Bekijk alle',
      onPressed: () => context.go(Routes.vehicleHistoryFor(garageId, vehicleId)),
    );
    final Widget child;
    if (repairs.hasValue) {
      final latest = repairs.requireValue.take(3).toList();
      child = latest.isEmpty
          ? Padding(
              padding: const EdgeInsets.symmetric(vertical: 16),
              child: Text(
                'Nog geen onderhoud of reparaties geregistreerd.',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            )
          : Column(
              children: [
                for (final (index, repair) in latest.indexed)
                  RepairTimelineTile(repair: repair, isFirst: index == 0, isLast: index == latest.length - 1),
              ],
            );
    } else if (repairs.hasError) {
      // A garage without access yet sees the explanation on the history screen.
      child = Align(
        alignment: Alignment.centerLeft,
        child: TextButton(
          onPressed: () => context.go(Routes.vehicleHistoryFor(garageId, vehicleId)),
          child: const Text('Onderhoudshistorie openen'),
        ),
      );
    } else {
      child = const Padding(padding: EdgeInsets.all(16), child: Center(child: CircularProgressIndicator()));
    }
    return SectionCard(title: 'Reparatiegeschiedenis', action: openHistory, child: child);
  }
}
