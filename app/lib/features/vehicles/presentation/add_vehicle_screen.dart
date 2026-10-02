import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/form_widgets.dart';
import '../application/vehicle_providers.dart';
import '../data/vehicle_api.dart';
import '../domain/vehicle.dart';

enum _Mode { register, claim }

/// Add a vehicle: register a new one (VIN not yet known) or claim one that is already in RepairTrack,
/// e.g. registered by a garage or sold by its previous owner.
class AddVehicleScreen extends ConsumerStatefulWidget {
  const AddVehicleScreen({super.key});

  @override
  ConsumerState<AddVehicleScreen> createState() => _AddVehicleScreenState();
}

class _AddVehicleScreenState extends ConsumerState<AddVehicleScreen> {
  _Mode _mode = _Mode.register;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Voertuig toevoegen')),
      body: CenteredForm(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SegmentedButton<_Mode>(
              segments: const [
                ButtonSegment(value: _Mode.register, label: Text('Nieuw registreren')),
                ButtonSegment(value: _Mode.claim, label: Text('Bestaand claimen')),
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
  const _RegisterForm({required this.onAlreadyRegistered});

  final VoidCallback onAlreadyRegistered;

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
              ownedSince: _ownedSince,
            ),
          );
      ref.invalidate(myVehiclesProvider);
      if (mounted) {
        context.go(Routes.vehicle(vehicle.id));
      }
    } on ApiException catch (e) {
      if (e.code == 'VEHICLE_ALREADY_REGISTERED' && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(userMessage(e))));
        widget.onAlreadyRegistered();
        return;
      }
      if (mounted) {
        setState(() => _error = userMessage(e));
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _pickOwnedSince() async {
    final picked = await showDatePicker(
      context: context,
      firstDate: DateTime(1950),
      lastDate: today(),
      initialDate: _ownedSince ?? today(),
      helpText: 'Eigenaar sinds',
    );
    if (picked != null) {
      setState(() => _ownedSince = picked);
    }
  }

  String? _required(String? value) => (value == null || value.trim().isEmpty) ? 'Verplicht veld' : null;

  @override
  Widget build(BuildContext context) {
    return Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextFormField(
            controller: _vin,
            decoration: const InputDecoration(
              labelText: 'VIN (chassisnummer)',
              helperText: '17 tekens, staat op je kentekenbewijs (E)',
            ),
            textCapitalization: TextCapitalization.characters,
            validator: (value) => isValidVin(value ?? '') ? null : 'Een VIN heeft 17 tekens, zonder I, O en Q',
          ),
          const SizedBox(height: 16),
          TextFormField(
            controller: _plate,
            decoration: const InputDecoration(labelText: 'Kenteken (optioneel)'),
            textCapitalization: TextCapitalization.characters,
          ),
          const SizedBox(height: 16),
          TextFormField(
            controller: _make,
            decoration: const InputDecoration(labelText: 'Merk'),
            validator: _required,
          ),
          const SizedBox(height: 16),
          TextFormField(
            controller: _model,
            decoration: const InputDecoration(labelText: 'Model'),
            validator: _required,
          ),
          const SizedBox(height: 16),
          TextFormField(
            controller: _year,
            decoration: const InputDecoration(labelText: 'Bouwjaar (optioneel)'),
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(4)],
          ),
          const SizedBox(height: 16),
          OutlinedButton.icon(
            onPressed: _pickOwnedSince,
            icon: const Icon(Icons.event),
            label: Text(_ownedSince == null ? 'Eigenaar sinds: vandaag' : 'Eigenaar sinds: ${formatDate(_ownedSince!)}'),
          ),
          const SizedBox(height: 16),
          if (_error != null) ...[
            ErrorText(message: _error!),
            const SizedBox(height: 16),
          ],
          FilledButton(
            onPressed: _busy ? null : _submit,
            child: _busy ? const ButtonProgress() : const Text('Registreren'),
          ),
        ],
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
        setState(() => _error = userMessage(e));
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
    if (!isValidVin(_vin.text)) {
      setState(() => _error = 'Een VIN heeft 17 tekens, zonder I, O en Q');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(vehicleApiProvider).claim(selected.id, vin: normalizeVin(_vin.text));
      ref.invalidate(myVehiclesProvider);
      if (mounted) {
        context.go(Routes.vehicle(selected.id));
      }
    } on ApiException catch (e) {
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
        const Text('Zoek het voertuig op kenteken. Als bewijs dat je de eigenaar bent vul je daarna het volledige VIN in.'),
        const SizedBox(height: 16),
        TextField(
          controller: _plate,
          decoration: InputDecoration(
            labelText: 'Kenteken',
            suffixIcon: IconButton(icon: const Icon(Icons.search), onPressed: _busy ? null : _search),
          ),
          textCapitalization: TextCapitalization.characters,
          onSubmitted: (_) => _search(),
        ),
        const SizedBox(height: 16),
        if (results != null && results.isEmpty) const Text('Geen voertuig gevonden met dit kenteken.'),
        if (results != null)
          for (final result in results)
            Card(
              child: ListTile(
                leading: Icon(_selected?.id == result.id ? Icons.radio_button_checked : Icons.radio_button_unchecked),
                title: Text(result.displayName),
                subtitle: Text([result.licensePlate, result.modelYear?.toString()].whereType<String>().join(' · ')),
                onTap: () => setState(() => _selected = result),
              ),
            ),
        if (_selected != null) ...[
          const SizedBox(height: 16),
          TextField(
            controller: _vin,
            decoration: const InputDecoration(labelText: 'VIN (chassisnummer)'),
            textCapitalization: TextCapitalization.characters,
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: _busy ? null : _claim,
            child: _busy ? const ButtonProgress() : const Text('Claimen'),
          ),
        ],
        if (_error != null) ...[
          const SizedBox(height: 16),
          ErrorText(message: _error!),
        ],
      ],
    );
  }
}
