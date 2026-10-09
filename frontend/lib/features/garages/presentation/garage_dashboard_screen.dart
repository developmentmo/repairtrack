import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/error_messages.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/async_value_view.dart';
import '../../../core/widgets/form_widgets.dart';
import '../../vehicles/data/vehicle_api.dart';
import '../../vehicles/domain/vehicle.dart';
import '../application/garage_providers.dart';
import '../data/garage_api.dart';
import '../domain/garage.dart';
import 'garage_labels.dart';

/// Garage home: find a vehicle by license plate, register a new one, and the vehicles the garage worked on.
class GarageDashboardScreen extends ConsumerWidget {
  const GarageDashboardScreen({super.key, required this.garageId});

  final String garageId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final garage = ref.watch(garageProvider(garageId));
    final memberships = ref.watch(myGaragesProvider);
    final membership = memberships.hasValue
        ? memberships.requireValue.where((m) => m.garageId == garageId).firstOrNull
        : null;

    return Scaffold(
      appBar: AppBar(title: Text(garage.hasValue ? garage.requireValue.name : 'Garage')),
      body: AsyncValueView(
        value: garage,
        onRetry: () => ref.invalidate(garageProvider(garageId)),
        data: (garage) => RefreshIndicator(
          onRefresh: () async {
            ref
              ..invalidate(garageProvider(garageId))
              ..invalidate(myGaragesProvider)
              ..invalidate(garageVehiclesProvider(garageId));
            await ref.read(garageVehiclesProvider(garageId).future);
          },
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              _GarageHeader(garage: garage, membership: membership),
              const SizedBox(height: 24),
              if (garage.canRecordWork) ...[
                _PlateSearch(garageId: garageId),
                const SizedBox(height: 8),
                OutlinedButton.icon(
                  onPressed: () => context.go(Routes.garageAddVehicle(garageId)),
                  icon: const Icon(Icons.add),
                  label: const Text('Nieuw voertuig registreren'),
                ),
                const SizedBox(height: 24),
              ],
              Text('Voertuigen waar we aan werkten', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              _GarageVehicles(garageId: garageId),
            ],
          ),
        ),
      ),
    );
  }
}

class _GarageHeader extends ConsumerWidget {
  const _GarageHeader({required this.garage, required this.membership});

  final Garage garage;
  final MyGarage? membership;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final status = garage.verificationStatus;
    final explanation = switch (status) {
      GarageVerificationStatus.verified => 'Je registraties tellen als "geverifieerde garage".',
      GarageVerificationStatus.pending =>
        'RepairTrack controleert je garage. Tot dan tellen je registraties als "garage".',
      GarageVerificationStatus.unverified =>
        'Je garage is niet geverifieerd. Registraties tellen als "garage".',
      GarageVerificationStatus.suspended => 'Je garage is geschorst en kan op dit moment geen werk vastleggen.',
      GarageVerificationStatus.unknown => '',
    };
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Text(garage.name, style: Theme.of(context).textTheme.titleLarge)),
                GarageStatusChip(status: status),
              ],
            ),
            const SizedBox(height: 4),
            Text('${garage.address}, ${garage.postalCode} ${garage.city} · KvK ${garage.kvkNumber}'),
            if (membership != null) Text('Jouw rol: ${garageRoleLabel(membership!.role)}'),
            if (explanation.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(explanation, style: Theme.of(context).textTheme.bodySmall),
            ],
            if (status == GarageVerificationStatus.unverified && (membership?.isAdmin ?? false)) ...[
              const SizedBox(height: 8),
              FilledButton.tonal(
                onPressed: () => _requestVerification(context, ref),
                child: const Text('Verificatie aanvragen'),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _requestVerification(BuildContext context, WidgetRef ref) async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(garageApiProvider).requestVerification(garage.id);
      ref
        ..invalidate(garageProvider(garage.id))
        ..invalidate(myGaragesProvider);
      messenger.showSnackBar(const SnackBar(content: Text('Verificatie aangevraagd.')));
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(userMessage(e))));
    }
  }
}

/// Search by license plate. Exact match; the result never shows a VIN.
class _PlateSearch extends ConsumerStatefulWidget {
  const _PlateSearch({required this.garageId});

  final String garageId;

  @override
  ConsumerState<_PlateSearch> createState() => _PlateSearchState();
}

class _PlateSearchState extends ConsumerState<_PlateSearch> {
  final _plate = TextEditingController();
  List<VehicleSearchResult>? _results;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _plate.dispose();
    super.dispose();
  }

  Future<void> _search() async {
    final plate = _plate.text.trim();
    if (plate.isEmpty) {
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final results = await ref.read(vehicleApiProvider).searchByLicensePlate(plate);
      if (mounted) {
        setState(() => _results = results);
      }
    } catch (e) {
      if (mounted) {
        setState(() => _error = userMessage(e));
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final results = _results;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextField(
          controller: _plate,
          decoration: InputDecoration(
            labelText: 'Zoek voertuig op kenteken',
            suffixIcon: _busy
                ? const Padding(padding: EdgeInsets.all(12), child: ButtonProgress())
                : IconButton(icon: const Icon(Icons.search), onPressed: _search),
          ),
          textCapitalization: TextCapitalization.characters,
          textInputAction: TextInputAction.search,
          onSubmitted: (_) => _search(),
        ),
        if (_error != null) ...[
          const SizedBox(height: 8),
          ErrorText(message: _error!),
        ],
        if (results != null && results.isEmpty)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 8),
            child: Text('Niet gevonden. Registreer het voertuig als het nog niet in RepairTrack staat.'),
          ),
        if (results != null)
          for (final vehicle in results) _VehicleTile(garageId: widget.garageId, vehicle: vehicle),
      ],
    );
  }
}

class _GarageVehicles extends ConsumerWidget {
  const _GarageVehicles({required this.garageId});

  final String garageId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final vehicles = ref.watch(garageVehiclesProvider(garageId));
    if (vehicles.hasError && !vehicles.hasValue) {
      return ErrorView(error: vehicles.error!, onRetry: () => ref.invalidate(garageVehiclesProvider(garageId)));
    }
    if (!vehicles.hasValue) {
      return const Center(child: CircularProgressIndicator());
    }
    final list = vehicles.requireValue;
    if (list.isEmpty) {
      return const Text('Nog geen voertuigen. Zoek een voertuig op kenteken om werk vast te leggen.');
    }
    return Column(
      children: [for (final vehicle in list) _VehicleTile(garageId: garageId, vehicle: vehicle)],
    );
  }
}

class _VehicleTile extends StatelessWidget {
  const _VehicleTile({required this.garageId, required this.vehicle});

  final String garageId;
  final VehicleSearchResult vehicle;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: const Icon(Icons.directions_car_outlined),
        title: Text(vehicle.displayName),
        subtitle: Text([vehicle.licensePlate, vehicle.modelYear?.toString()].whereType<String>().join(' · ')),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => context.go(Routes.garageVehicle(garageId, vehicle.id)),
      ),
    );
  }
}
