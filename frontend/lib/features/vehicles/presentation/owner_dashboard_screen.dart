import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/auth/session_controller.dart';
import '../../../core/format/formatters.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/app_shell.dart';
import '../../../core/widgets/async_value_view.dart';
import '../../../core/widgets/brand_widgets.dart';
import '../../disputes/application/dispute_providers.dart';
import '../../disputes/domain/dispute.dart';
import '../../garages/application/garage_providers.dart';
import '../../garages/domain/garage.dart';
import '../../garages/presentation/garage_labels.dart';
import '../../repairs/presentation/repair_tiles.dart';
import '../application/dashboard_providers.dart';
import '../application/vehicle_providers.dart';
import '../domain/vehicle.dart';
import 'vehicle_thumbnail.dart';

/// Home: greeting, key numbers, the vehicles the user currently owns, recent repairs and, for garage members,
/// their garages.
class OwnerDashboardScreen extends ConsumerWidget {
  const OwnerDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionControllerProvider);
    final user = session is SignedIn ? session.user : null;
    final vehicles = ref.watch(myVehiclesProvider);
    final repairs = ref.watch(dashboardRepairsProvider);
    final garages = ref.watch(myGaragesProvider);
    final myGarages = garages.hasValue ? garages.requireValue : const <MyGarage>[];
    final disputes = ref.watch(myDisputesProvider).value ?? const <PartyDispute>[];
    final wide = isWideLayout(context);
    final l = context.l10n;

    return Scaffold(
      appBar: AppBar(
        title: wide ? Text(l.navDashboard) : const BrandLogo(size: 26),
        actions: [
          PopupMenuButton<String>(
            tooltip: l.dashboardMenu,
            onSelected: (value) {
              if (value == 'garage') {
                context.go(Routes.newGarage);
              } else {
                ref.read(sessionControllerProvider.notifier).logout();
              }
            },
            itemBuilder: (context) => [
              PopupMenuItem(value: 'garage', child: Text(l.navRegisterGarage)),
              PopupMenuItem(value: 'logout', child: Text(l.navSignOut)),
            ],
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: UserAvatar(
                initials: user == null ? '' : userInitials(user.firstName, user.lastName),
                size: 34,
              ),
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.go(Routes.addVehicle),
        icon: const Icon(Icons.add),
        label: Text(l.commonVehicle),
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref
            ..invalidate(myGaragesProvider)
            ..invalidate(myDisputesProvider)
            ..invalidate(myVehiclesProvider)
            ..invalidate(vehiclePhotoProvider);
          await ref.read(myVehiclesProvider.future);
        },
        child: AsyncValueView(
          value: vehicles,
          onRetry: () => ref.invalidate(myVehiclesProvider),
          data: (list) {
            final repairList = repairs.value ?? const <VehicleRepair>[];
            final vehiclesSection = _VehiclesSection(vehicles: list, repairs: repairList);
            final recentSection = _RecentRepairsSection(repairs: repairs);
            return ListView(
              padding: EdgeInsets.fromLTRB(wide ? 24 : 16, 8, wide ? 24 : 16, 96),
              children: [
                ContentWidth(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(
                        user == null ? l.dashboardWelcome : l.dashboardWelcomeName(user.firstName),
                        style: Theme.of(context).textTheme.headlineSmall,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        l.dashboardSubtitle,
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                              color: Theme.of(context).textTheme.bodySmall?.color,
                            ),
                      ),
                      const SizedBox(height: 20),
                      _StatTiles(
                        vehicleCount: list.length,
                        repairCount: repairs.hasValue ? repairList.length : null,
                        garageCount: myGarages.length,
                        disputesNeedingAction: disputes.where((d) => d.canRespond).length,
                      ),
                      const SizedBox(height: 16),
                      if (wide)
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Expanded(flex: 3, child: vehiclesSection),
                            const SizedBox(width: 16),
                            Expanded(flex: 2, child: recentSection),
                          ],
                        )
                      else ...[
                        vehiclesSection,
                        const SizedBox(height: 4),
                        recentSection,
                      ],
                      if (myGarages.isNotEmpty) ...[
                        const SizedBox(height: 4),
                        SectionCard(
                          title: l.dashboardMyGarages,
                          child: Column(children: [for (final garage in myGarages) _GarageRow(garage: garage)]),
                        ),
                      ],
                    ],
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _StatTiles extends StatelessWidget {
  const _StatTiles({
    required this.vehicleCount,
    required this.repairCount,
    required this.garageCount,
    required this.disputesNeedingAction,
  });

  final int vehicleCount;
  final int? repairCount;
  final int garageCount;
  final int disputesNeedingAction;

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final tiles = [
      _StatTile(
        icon: Icons.directions_car_outlined,
        value: '$vehicleCount',
        label: l.dashboardStatVehicles(vehicleCount),
        caption: l.dashboardStatVehiclesCaption,
      ),
      _StatTile(
        icon: Icons.build_outlined,
        value: repairCount == null ? '–' : '$repairCount',
        label: l.dashboardStatRepairs(repairCount ?? 0),
        caption: l.dashboardStatRepairsCaption,
      ),
      _StatTile(
        icon: Icons.home_repair_service_outlined,
        value: '$garageCount',
        label: l.dashboardStatGarages(garageCount),
        caption: l.dashboardStatGaragesCaption,
      ),
      if (disputesNeedingAction == 0)
        _StatTile(
          icon: Icons.verified_outlined,
          tone: Tone.success,
          label: l.dashboardAllUpToDate,
          caption: l.dashboardNoNotifications,
        )
      else
        _StatTile(
          icon: Icons.gavel,
          tone: Tone.warning,
          label: l.dashboardActionNeeded,
          caption: l.dashboardDisputesWaiting(disputesNeedingAction),
          onTap: () => context.go(Routes.disputes),
        ),
    ];
    return LayoutBuilder(
      builder: (context, constraints) {
        final columns = constraints.maxWidth >= 720 ? 4 : 2;
        const gap = 12.0;
        final width = (constraints.maxWidth - gap * (columns - 1)) / columns;
        return Wrap(
          spacing: gap,
          runSpacing: gap,
          children: [for (final tile in tiles) SizedBox(width: width, child: tile)],
        );
      },
    );
  }
}

class _StatTile extends StatelessWidget {
  const _StatTile({
    required this.icon,
    required this.label,
    required this.caption,
    this.value,
    this.tone = Tone.info,
    this.onTap,
  });

  final IconData icon;
  final String? value;
  final String label;
  final String caption;
  final Tone tone;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              IconBadge(icon: icon, tone: tone, size: 44),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (value != null) Text(value!, style: theme.textTheme.titleLarge),
                    Text(label, style: theme.textTheme.titleSmall),
                    Text(caption, style: theme.textTheme.bodySmall, maxLines: 2, overflow: TextOverflow.ellipsis),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _VehiclesSection extends StatelessWidget {
  const _VehiclesSection({required this.vehicles, required this.repairs});

  final List<Vehicle> vehicles;
  final List<VehicleRepair> repairs;

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return SectionCard(
      title: l.dashboardMyVehicles,
      action: SectionLink(label: l.commonAddVehicle, onPressed: () => context.go(Routes.addVehicle)),
      child: vehicles.isEmpty
          ? const _EmptyState()
          : LayoutBuilder(
              builder: (context, constraints) {
                final columns = constraints.maxWidth >= 520 ? 2 : 1;
                const gap = 12.0;
                final width = (constraints.maxWidth - gap * (columns - 1)) / columns;
                return Wrap(
                  spacing: gap,
                  runSpacing: gap,
                  children: [
                    for (final vehicle in vehicles)
                      SizedBox(
                        width: width,
                        child: _VehicleCard(
                          vehicle: vehicle,
                          lastRepair: repairs.where((r) => r.vehicle.id == vehicle.id).firstOrNull,
                        ),
                      ),
                  ],
                );
              },
            ),
    );
  }
}

class _VehicleCard extends StatelessWidget {
  const _VehicleCard({required this.vehicle, this.lastRepair});

  final Vehicle vehicle;
  final VehicleRepair? lastRepair;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final last = lastRepair?.repair;
    final l = context.l10n;
    return Card(
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => context.go(Routes.vehicle(vehicle.id)),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            VehicleThumbnail(vehicle: vehicle),
            Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(vehicle.displayName, style: theme.textTheme.titleMedium),
                  if (vehicle.modelYear != null) Text('${vehicle.modelYear}', style: theme.textTheme.bodySmall),
                  if (vehicle.licensePlate != null) ...[
                    const SizedBox(height: 8),
                    LicensePlate(plate: vehicle.licensePlate!),
                  ],
                  const SizedBox(height: 12),
                  Text(l.dashboardLastMaintenance, style: theme.textTheme.bodySmall),
                  Text(
                    last == null ? l.dashboardNothingRecorded : '${formatDate(last.eventDate)} · ${formatKm(last.mileage)}',
                    style: theme.textTheme.bodyMedium,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _RecentRepairsSection extends StatelessWidget {
  const _RecentRepairsSection({required this.repairs});

  final AsyncValue<List<VehicleRepair>> repairs;

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final Widget child;
    if (repairs.hasValue) {
      final recent = repairs.requireValue.take(5).toList();
      child = recent.isEmpty
          ? Padding(
              padding: const EdgeInsets.symmetric(vertical: 24),
              child: Text(
                l.vehicleNoRepairsYet,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            )
          : Column(
              children: [
                for (final (index, item) in recent.indexed) ...[
                  if (index > 0) const Divider(),
                  RepairRow(repair: item.repair, vehicleName: item.vehicle.displayName),
                ],
              ],
            );
    } else if (repairs.hasError) {
      child = Text(l.dashboardRecentRepairsFailed, style: Theme.of(context).textTheme.bodySmall);
    } else {
      child = const Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator()));
    }
    return SectionCard(title: l.dashboardRecentRepairs, child: child);
  }
}

class _GarageRow extends StatelessWidget {
  const _GarageRow({required this.garage});

  final MyGarage garage;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 4),
      leading: const IconBadge(icon: Icons.home_repair_service_outlined),
      title: Text(garage.name),
      subtitle: Text([garage.city, garageRoleLabel(context.l10n, garage.role)].whereType<String>().join(' · ')),
      trailing: GarageStatusChip(status: garage.verificationStatus),
      onTap: () => context.go(Routes.garage(garage.garageId)),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 32),
      child: Column(
        children: [
          const IconBadge(icon: Icons.directions_car_outlined, size: 64),
          const SizedBox(height: 12),
          Text(l.dashboardNoVehicles, style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 4),
          Text(
            l.dashboardNoVehiclesHint,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: () => context.go(Routes.addVehicle),
            icon: const Icon(Icons.add),
            label: Text(l.commonAddVehicle),
          ),
        ],
      ),
    );
  }
}
