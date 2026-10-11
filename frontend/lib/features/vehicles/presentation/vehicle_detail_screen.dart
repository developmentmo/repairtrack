import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/l10n/l10n.dart';
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
import 'vehicle_photo_card.dart';

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
    final l = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(garageId == null ? l.vehicleDetailMyVehicle : l.commonVehicle)),
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
                    ? l.vehicleDetailDisputeRespond
                    : l.vehicleDetailDisputeUnderReview,
              ),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(onPressed: () => context.go(Routes.disputes), child: Text(l.vehicleDetailToDisputes)),
              ),
              const SizedBox(height: 8),
            ],
            if (garageId == null && vehicle.ownedByMe) ...[
              VehiclePhotoCard(vehicleId: vehicle.id),
              const SizedBox(height: 12),
            ],
            _VehicleHeader(vehicle: vehicle, showPicture: !(garageId == null && vehicle.ownedByMe)),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                if (garageId != null)
                  FilledButton.icon(
                    onPressed: () => context.go(Routes.garageNewRepair(garageId!, vehicle.id)),
                    icon: const Icon(Icons.build_outlined),
                    label: Text(l.vehicleDetailRecordWork),
                  ),
                if (garageId == null && vehicle.ownedByMe) ...[
                  FilledButton.icon(
                    onPressed: () => context.go(Routes.newRepair(vehicle.id)),
                    icon: const Icon(Icons.add),
                    label: Text(l.vehicleDetailNewRepair),
                  ),
                  OutlinedButton.icon(
                    onPressed: () => context.go(Routes.shareVehicle(vehicle.id)),
                    icon: const Icon(Icons.share_outlined),
                    label: Text(l.vehicleDetailShareHistory),
                  ),
                ],
              ],
            ),
            const SizedBox(height: 12),
            _HistoryPreview(vehicleId: vehicle.id, garageId: garageId),
            SectionCard(
              title: l.vehicleDetailDetails,
              child: Column(
                children: [
                  DetailRow(label: l.vehicleLicensePlate, value: vehicle.licensePlate ?? '-'),
                  DetailRow(label: l.vehicleDetailModelYear, value: vehicle.modelYear?.toString() ?? '-'),
                  DetailRow(
                    label: l.vehicleDetailFirstRegistration,
                    value: vehicle.firstRegistrationDate == null ? '-' : formatDate(vehicle.firstRegistrationDate!),
                  ),
                  if (vehicle.vin != null) DetailRow(label: l.vehicleDetailVin, value: vehicle.vin!),
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
                  label: Text(l.vehicleDetailSold),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _endOwnership(BuildContext context, WidgetRef ref, Vehicle vehicle) async {
    final l = context.l10n;
    final messenger = ScaffoldMessenger.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l.vehicleDetailSoldTitle),
        content: Text(l.vehicleDetailSoldText),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l.commonCancel)),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(l.vehicleDetailEndOwnership)),
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
      messenger.showSnackBar(SnackBar(content: Text(userMessage(l, e))));
    }
  }
}

class _VehicleHeader extends StatelessWidget {
  const _VehicleHeader({required this.vehicle, required this.showPicture});

  final Vehicle vehicle;

  /// False when the owner's photo card is shown above the header.
  final bool showPicture;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            if (showPicture) ...[
              const VehiclePicture(width: 120, height: 84),
              const SizedBox(width: 16),
            ],
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
              StatusPill(label: context.l10n.vehicleDetailActive, tone: Tone.success, icon: Icons.check_circle_outline),
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
    final l = context.l10n;
    final openHistory = SectionLink(
      label: l.vehicleDetailViewAll,
      onPressed: () => context.go(Routes.vehicleHistoryFor(garageId, vehicleId)),
    );
    final Widget child;
    if (repairs.hasValue) {
      final latest = repairs.requireValue.take(3).toList();
      child = latest.isEmpty
          ? Padding(
              padding: const EdgeInsets.symmetric(vertical: 16),
              child: Text(
                l.vehicleNoRepairsYet,
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
          child: Text(l.vehicleDetailOpenHistory),
        ),
      );
    } else {
      child = const Padding(padding: EdgeInsets.all(16), child: Center(child: CircularProgressIndicator()));
    }
    return SectionCard(title: l.vehicleDetailRepairHistory, action: openHistory, child: child);
  }
}
