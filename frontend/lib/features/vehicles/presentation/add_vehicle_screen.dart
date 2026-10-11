import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/form_widgets.dart';
import '../../garages/application/garage_providers.dart';
import '../application/vehicle_providers.dart';
import '../data/vehicle_api.dart';
import '../domain/vehicle.dart';

enum _Mode { register, claim }

/// Add a vehicle: register a new one (VIN not yet known) or claim one that is already in RepairTrack,
/// e.g. registered by a garage or sold by its previous owner.
class AddVehicleScreen extends ConsumerStatefulWidget {
  const AddVehicleScreen({super.key, this.garageId});

  /// Register on behalf of this garage: the vehicle gets no owner (the owner claims it later with the VIN).
  final String? garageId;

  @override
  ConsumerState<AddVehicleScreen> createState() => _AddVehicleScreenState();
}

class _AddVehicleScreenState extends ConsumerState<AddVehicleScreen> {
  _Mode _mode = _Mode.register;

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final garageId = widget.garageId;
    if (garageId != null) {
      return Scaffold(
        appBar: AppBar(title: Text(l.addVehicleRegisterTitle)),
        body: CenteredForm(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              InfoBanner(message: l.addVehicleGarageInfo),
              const SizedBox(height: 24),
              _RegisterForm(garageId: garageId),
            ],
          ),
        ),
      );
    }
    return Scaffold(
      appBar: AppBar(title: Text(l.commonAddVehicle)),
      body: CenteredForm(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SegmentedButton<_Mode>(
              segments: [
                ButtonSegment(value: _Mode.register, label: Text(l.addVehicleModeRegister)),
                ButtonSegment(value: _Mode.claim, label: Text(l.addVehicleModeClaim)),
              ],
              selected: {_mode},
              onSelectionChanged: (selection) => setState(() => _mode = selection.first),
            ),
            const SizedBox(height: 24),
            switch (_mode) {
              _Mode.register => _RegisterForm(onAlreadyRegistered: () => setState(() => _mode = _Mode.claim)),
              _Mode.claim => const _ClaimForm(),
            },
          ],
        ),
      ),
    );
  }
}

class _RegisterForm extends ConsumerStatefulWidget {
  const _RegisterForm({this.onAlreadyRegistered, this.garageId});

  /// Owner: switch to "claim" when the VIN already exists.
  final VoidCallback? onAlreadyRegistered;
  final String? garageId;

  @override
  ConsumerState<_RegisterForm> createState() => _RegisterFormState();
}

class _RegisterFormState extends ConsumerState<_RegisterForm> {
  final _formKey = GlobalKey<FormState>();
  final _vin = TextEditingController();
  final _plate = TextEditingController();
  final _make = TextEditingController();
  final _model = TextEditingController();
  final _year = TextEditingController();
  DateTime? _ownedSince;
  bool _busy = false;
  String? _error;

  /// RDW data for the plate as it was when looked up; cleared when the plate changes.
  RegistryVehicle? _registry;
  bool _lookingUp = false;
  String? _lookupMessage;

  @override
  void dispose() {
    for (final controller in [_vin, _plate, _make, _model, _year]) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final l = context.l10n;
    final messenger = ScaffoldMessenger.of(context);
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final vehicle = await ref.read(vehicleApiProvider).register(
            NewVehicle(
              vin: normalizeVin(_vin.text),
              make: _make.text.trim(),
              model: _model.text.trim(),
              licensePlate: _plate.text.trim().isEmpty ? null : _plate.text.trim(),
              modelYear: int.tryParse(_year.text),
              firstRegistrationDate: _registry?.firstRegistrationDate,
              ownedSince: widget.garageId == null ? _ownedSince : null,
              garageId: widget.garageId,
            ),
          );
      ref.invalidate(myVehiclesProvider);
      if (widget.garageId != null) {
        ref.invalidate(garageVehiclesProvider(widget.garageId!));
      }
      if (mounted) {
        context.go(Routes.vehicleFor(widget.garageId, vehicle.id));
      }
    } on ApiException catch (e) {
      final onAlreadyRegistered = widget.onAlreadyRegistered;
      if (e.code == 'VEHICLE_ALREADY_REGISTERED' && onAlreadyRegistered != null && mounted) {
        messenger.showSnackBar(SnackBar(content: Text(userMessage(l, e))));
        onAlreadyRegistered();
        return;
      }
      if (mounted) {
        setState(() => _error = userMessage(l, e));
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  /// Fills make, model and model year from the RDW. Only a suggestion: the user can still change everything.
  Future<void> _lookup() async {
    final l = context.l10n;
    if (_plate.text.trim().isEmpty) {
      setState(() => _lookupMessage = l.addVehicleEnterPlateFirst);
      return;
    }
    setState(() {
      _lookingUp = true;
      _lookupMessage = null;
      _registry = null;
    });
    try {
      final vehicle = await ref.read(vehicleApiProvider).registryLookup(_plate.text);
      if (!mounted) {
        return;
      }
      setState(() {
        _registry = vehicle;
        if (vehicle.make != null) {
          _make.text = vehicle.make!;
        }
        if (vehicle.model != null) {
          _model.text = vehicle.model!;
        }
        // The RDW has no model year; the year of first admission is the usual stand-in.
        final firstRegistration = vehicle.firstRegistrationDate;
        if (firstRegistration != null) {
          _year.text = '${firstRegistration.year}';
        }
      });
    } on ApiException catch (e) {
      if (mounted) {
        setState(() => _lookupMessage = userMessage(l, e));
      }
    } finally {
      if (mounted) {
        setState(() => _lookingUp = false);
      }
    }
  }

  Future<void> _pickOwnedSince() async {
    final picked = await showDatePicker(
      context: context,
      firstDate: DateTime(1950),
      lastDate: today(),
      initialDate: _ownedSince ?? today(),
      helpText: context.l10n.addVehicleOwnedSince,
    );
    if (picked != null) {
      setState(() => _ownedSince = picked);
    }
  }

  String? _required(String? value) =>
      (value == null || value.trim().isEmpty) ? context.l10n.commonRequiredField : null;

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextFormField(
            key: const Key('vehicle-plate'),
            controller: _plate,
            decoration: InputDecoration(
              labelText: l.addVehiclePlateOptional,
              helperText: l.addVehiclePlateHelper,
              suffixIcon: _lookingUp
                  ? const Padding(padding: EdgeInsets.all(12), child: ButtonProgress())
                  : IconButton(
                      key: const Key('rdw-lookup'),
                      tooltip: l.addVehicleRdwLookup,
                      icon: const Icon(Icons.travel_explore),
                      onPressed: _lookup,
                    ),
            ),
            textCapitalization: TextCapitalization.characters,
            onChanged: (_) {
              if (_registry != null || _lookupMessage != null) {
                setState(() {
                  _registry = null;
                  _lookupMessage = null;
                });
              }
            },
            onFieldSubmitted: (_) => _lookup(),
          ),
          if (_lookupMessage != null) ...[
            const SizedBox(height: 8),
            Text(_lookupMessage!, key: const Key('rdw-message')),
          ],
          if (_registry != null) ...[
            const SizedBox(height: 8),
            _RegistrySummary(vehicle: _registry!),
          ],
          const SizedBox(height: 16),
          TextFormField(
            controller: _vin,
            decoration: InputDecoration(
              labelText: l.commonVin,
              helperText: l.addVehicleVinHelper,
            ),
            textCapitalization: TextCapitalization.characters,
            validator: (value) => isValidVin(value ?? '') ? null : l.addVehicleVinInvalid,
          ),
          const SizedBox(height: 16),
          TextFormField(
            key: const Key('vehicle-make'),
            controller: _make,
            decoration: InputDecoration(labelText: l.addVehicleMake),
            validator: _required,
          ),
          const SizedBox(height: 16),
          TextFormField(
            key: const Key('vehicle-model'),
            controller: _model,
            decoration: InputDecoration(labelText: l.addVehicleModel),
            validator: _required,
          ),
          const SizedBox(height: 16),
          TextFormField(
            key: const Key('vehicle-year'),
            controller: _year,
            decoration: InputDecoration(labelText: l.addVehicleYearOptional),
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(4)],
          ),
          if (widget.garageId == null) ...[
            const SizedBox(height: 16),
            OutlinedButton.icon(
              onPressed: _pickOwnedSince,
              icon: const Icon(Icons.event),
              label: Text(
                _ownedSince == null
                    ? l.addVehicleOwnedSinceToday
                    : l.addVehicleOwnedSinceDate(formatDate(_ownedSince!)),
              ),
            ),
          ],
          const SizedBox(height: 16),
          if (_error != null) ...[
            ErrorText(message: _error!),
            const SizedBox(height: 16),
          ],
          FilledButton(
            onPressed: _busy ? null : _submit,
            child: _busy ? const ButtonProgress() : Text(l.addVehicleRegisterButton),
          ),
        ],
      ),
    );
  }
}

/// What the RDW told us, so the user can check it. The VIN is not in the RDW open data.
class _RegistrySummary extends StatelessWidget {
  const _RegistrySummary({required this.vehicle});

  final RegistryVehicle vehicle;

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final facts = <String>[
      if (vehicle.vehicleType != null) vehicle.vehicleType!,
      if (vehicle.primaryColor != null) vehicle.primaryColor!.toLowerCase(),
      if (vehicle.fuelTypes.isNotEmpty) vehicle.fuelTypes.join(' / ').toLowerCase(),
      if (vehicle.firstRegistrationDate != null) l.addVehicleRdwFirstRegistration(formatDate(vehicle.firstRegistrationDate!)),
      if (vehicle.apkExpiryDate != null) l.addVehicleRdwApkUntil(formatDate(vehicle.apkExpiryDate!)),
    ];
    return Card(
      key: const Key('rdw-summary'),
      child: ListTile(
        leading: const Icon(Icons.fact_check_outlined),
        title: Text(l.addVehicleRdwFound([vehicle.make, vehicle.model].whereType<String>().join(' '))),
        subtitle: Text([
          if (facts.isNotEmpty) facts.join(' · '),
          l.addVehicleRdwCheck,
        ].join('\n')),
      ),
    );
  }
}

class _ClaimForm extends ConsumerStatefulWidget {
  const _ClaimForm();

  @override
  ConsumerState<_ClaimForm> createState() => _ClaimFormState();
}

class _ClaimFormState extends ConsumerState<_ClaimForm> {
  final _plate = TextEditingController();
  final _vin = TextEditingController();
  List<VehicleSearchResult>? _results;
  VehicleSearchResult? _selected;
  bool _busy = false;
  String? _error;

  /// The selected vehicle already has an owner: offer to dispute that ownership.
  bool _ownedBySomeoneElse = false;

  @override
  void dispose() {
    _plate.dispose();
    _vin.dispose();
    super.dispose();
  }

  Future<void> _search() async {
    if (_plate.text.trim().isEmpty) {
      return;
    }
    final l = context.l10n;
    setState(() {
      _busy = true;
      _error = null;
      _selected = null;
    });
    try {
      final results = await ref.read(vehicleApiProvider).searchByLicensePlate(_plate.text.trim());
      if (mounted) {
        setState(() => _results = results);
      }
    } on ApiException catch (e) {
      if (mounted) {
        setState(() => _error = userMessage(l, e));
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _claim() async {
    final selected = _selected;
    if (selected == null) {
      return;
    }
    final l = context.l10n;
    if (!isValidVin(_vin.text)) {
      setState(() => _error = l.addVehicleVinInvalid);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
      _ownedBySomeoneElse = false;
    });
    try {
      await ref.read(vehicleApiProvider).claim(selected.id, vin: normalizeVin(_vin.text));
      ref.invalidate(myVehiclesProvider);
      if (mounted) {
        context.go(Routes.vehicle(selected.id));
      }
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _error = userMessage(l, e);
          _ownedBySomeoneElse = e.code == 'VEHICLE_ALREADY_OWNED';
        });
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final results = _results;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(l.addVehicleClaimIntro),
        const SizedBox(height: 16),
        TextField(
          controller: _plate,
          decoration: InputDecoration(
            labelText: l.vehicleLicensePlate,
            suffixIcon: IconButton(icon: const Icon(Icons.search), onPressed: _busy ? null : _search),
          ),
          textCapitalization: TextCapitalization.characters,
          onSubmitted: (_) => _search(),
        ),
        const SizedBox(height: 16),
        if (results != null && results.isEmpty) Text(l.addVehicleNoneFound),
        if (results != null)
          for (final result in results)
            Card(
              child: ListTile(
                leading: Icon(_selected?.id == result.id ? Icons.radio_button_checked : Icons.radio_button_unchecked),
                title: Text(result.displayName),
                subtitle: Text([result.licensePlate, result.modelYear?.toString()].whereType<String>().join(' · ')),
                onTap: () => setState(() {
                  _selected = result;
                  _ownedBySomeoneElse = false;
                }),
              ),
            ),
        if (_selected != null) ...[
          const SizedBox(height: 16),
          TextField(
            controller: _vin,
            decoration: InputDecoration(labelText: l.commonVin),
            textCapitalization: TextCapitalization.characters,
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: _busy ? null : _claim,
            child: _busy ? const ButtonProgress() : Text(l.addVehicleClaimButton),
          ),
        ],
        if (_error != null) ...[
          const SizedBox(height: 16),
          ErrorText(message: _error!),
        ],
        if (_ownedBySomeoneElse && _selected != null) ...[
          const SizedBox(height: 8),
          Text(l.addVehicleDisputeHint),
          TextButton.icon(
            key: const Key('open-dispute'),
            onPressed: () => context.go(Routes.openDispute(_selected!.id), extra: _selected!.displayName),
            icon: const Icon(Icons.gavel),
            label: Text(l.addVehicleDisputeButton),
          ),
        ],
      ],
    );
  }
}
