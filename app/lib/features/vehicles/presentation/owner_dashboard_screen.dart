import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/auth/session_controller.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/async_value_view.dart';
import '../../garages/application/garage_providers.dart';
import '../../garages/domain/garage.dart';
import '../../garages/presentation/garage_labels.dart';
import '../application/vehicle_providers.dart';
import '../domain/vehicle.dart';

/// Home: greeting, the vehicles the user currently owns ("My Vehicles") and, for garage members, their garages.
class OwnerDashboardScreen extends ConsumerWidget {
  const OwnerDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionControllerProvider);
    final firstName = session is SignedIn ? session.user.firstName : '';
    final vehicles = ref.watch(myVehiclesProvider);
    final garages = ref.watch(myGaragesProvider);
    final myGarages = garages.hasValue ? garages.requireValue : const <MyGarage>[];

    return Scaffold(
      appBar: AppBar(
        title: const Text('RepairTrack'),
        actions: [
          PopupMenuButton<String>(
            onSelected: (value) {
              if (value == 'garage') {
                context.go(Routes.newGarage);
              } else {
                ref.read(sessionControllerProvider.notifier).logout();
              }
            },
            itemBuilder: (context) => const [
              PopupMenuItem(value: 'garage', child: Text('Garage aanmelden')),
              PopupMenuItem(value: 'logout', child: Text('Uitloggen')),
            ],
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.go(Routes.addVehicle),
        icon: const Icon(Icons.add),
        label: const Text('Voertuig'),
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref
            ..invalidate(myGaragesProvider)
            ..invalidate(myVehiclesProvider);
          await ref.read(myVehiclesProvider.future);
        },
        child: AsyncValueView(
          value: vehicles,
          onRetry: () => ref.invalidate(myVehiclesProvider),
          data: (list) => ListView(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
            children: [
              Text('Hallo $firstName', style: Theme.of(context).textTheme.headlineSmall),
              const SizedBox(height: 4),
              const Text('Je voertuigen en hun onderhoudshistorie.'),
              const SizedBox(height: 16),
              if (myGarages.isNotEmpty) ...[
                Text('Mijn garages', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 8),
                for (final garage in myGarages) _GarageCard(garage: garage),
                const SizedBox(height: 24),
                Text('Mijn voertuigen', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 8),
              ],
              if (list.isEmpty) const _EmptyState() else ...list.map((vehicle) => _VehicleCard(vehicle: vehicle)),
            ],
          ),
        ),
      ),
    );
  }
}

class _VehicleCard extends StatelessWidget {
  const _VehicleCard({required this.vehicle});

  final Vehicle vehicle;

  @override
  Widget build(BuildContext context) {
    final details = [
      if (vehicle.licensePlate != null) vehicle.licensePlate!,
      if (vehicle.modelYear != null) '${vehicle.modelYear}',
    ].join(' · ');
    return Card(
      child: ListTile(
        leading: const Icon(Icons.directions_car_outlined),
        title: Text(vehicle.displayName),
        subtitle: details.isEmpty ? null : Text(details),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => context.go(Routes.vehicle(vehicle.id)),
      ),
    );
  }
}

class _GarageCard extends StatelessWidget {
  const _GarageCard({required this.garage});

  final MyGarage garage;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: const Icon(Icons.home_repair_service_outlined),
        title: Text(garage.name),
        subtitle: Text([garage.city, garageRoleLabel(garage.role)].whereType<String>().join(' · ')),
        trailing: GarageStatusChip(status: garage.verificationStatus),
        onTap: () => context.go(Routes.garage(garage.garageId)),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 48),
      child: Column(
        children: [
          Icon(Icons.directions_car_outlined, size: 64, color: Theme.of(context).colorScheme.outline),
          const SizedBox(height: 12),
          const Text('Je hebt nog geen voertuigen.', textAlign: TextAlign.center),
          const SizedBox(height: 4),
          const Text(
            'Registreer een nieuw voertuig of claim een voertuig dat al in RepairTrack staat.',
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: () => context.go(Routes.addVehicle),
            icon: const Icon(Icons.add),
            label: const Text('Voertuig toevoegen'),
          ),
        ],
      ),
    );
  }
}
