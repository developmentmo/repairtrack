import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/form_widgets.dart';
import '../application/repair_providers.dart';
import '../data/repair_api.dart';
import '../domain/repair.dart';
import 'repair_labels.dart';

/// Record maintenance or a repair. Used by the owner now and by garages in Phase 8b ([garageId]).
/// The backend decides source type and verification status; this form cannot set them.
class CreateRepairScreen extends ConsumerStatefulWidget {
  const CreateRepairScreen({super.key, required this.vehicleId, this.garageId});

  final String vehicleId;
  final String? garageId;

  @override
  ConsumerState<CreateRepairScreen> createState() => _CreateRepairScreenState();
}

class _CreateRepairScreenState extends ConsumerState<CreateRepairScreen> {
  /// Backend limit.
  static const maxMileage = 2000000;

  final _formKey = GlobalKey<FormState>();
  final _title = TextEditingController();
  final _description = TextEditingController();
  final _mileage = TextEditingController();
  RepairEventType _eventType = RepairEventType.maintenance;
  DateTime _eventDate = today();
  final List<NewPart> _parts = [];
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _title.dispose();
    _description.dispose();
    _mileage.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      firstDate: DateTime(1950),
      lastDate: today(),
      initialDate: _eventDate,
    );
    if (picked != null) {
      setState(() => _eventDate = picked);
    }
  }

  Future<void> _addPart() async {
    final part = await showDialog<NewPart>(context: context, builder: (context) => const _PartDialog());
    if (part != null) {
      setState(() => _parts.add(part));
    }
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
      final created = await ref.read(repairApiProvider).create(
            widget.vehicleId,
            NewRepair(
              eventType: _eventType,
              eventDate: _eventDate,
              mileage: int.parse(_mileage.text),
              title: _title.text.trim(),
              description: _description.text.trim(),
              garageId: widget.garageId,
              parts: List.of(_parts),
            ),
          );
      ref
        ..invalidate(vehicleRepairsProvider(widget.vehicleId))
        ..invalidate(mileageHistoryProvider(widget.vehicleId));
      if (!mounted) {
        return;
      }
      if (created.warnings.isNotEmpty) {
        await _showWarnings(created.warnings);
      }
      if (mounted) {
        context.go(Routes.vehicleHistory(widget.vehicleId));
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

  Future<void> _showWarnings(List<MileageWarning> warnings) {
    return showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.speed),
        title: const Text('Opgeslagen, met een waarschuwing'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            for (final warning in warnings) ...[Text(mileageWarningText(warning)), const SizedBox(height: 8)],
            const Text('Controleer de kilometerstand. Is die fout, dan kun je de registratie later corrigeren.'),
          ],
        ),
        actions: [FilledButton(onPressed: () => Navigator.pop(context), child: const Text('Begrepen'))],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Onderhoud toevoegen')),
      body: CenteredForm(
        maxWidth: 560,
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              DropdownButtonFormField<RepairEventType>(
                initialValue: _eventType,
                decoration: const InputDecoration(labelText: 'Soort'),
                items: [
                  for (final type in RepairEventType.selectable)
                    DropdownMenuItem(value: type, child: Text(eventTypeLabel(type))),
                ],
                onChanged: (type) => setState(() => _eventType = type ?? _eventType),
              ),
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: _pickDate,
                icon: const Icon(Icons.event),
                label: Text('Datum: ${formatDate(_eventDate)}'),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _mileage,
                decoration: const InputDecoration(labelText: 'Kilometerstand', suffixText: 'km'),
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                validator: (value) {
                  final km = int.tryParse(value ?? '');
                  if (km == null) {
                    return 'Vul de kilometerstand in';
                  }
                  return km > maxMileage ? 'Maximaal ${formatThousands(maxMileage)} km' : null;
                },
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _title,
                decoration: const InputDecoration(labelText: 'Titel', hintText: 'Bijv. Grote beurt'),
                maxLength: 150,
                validator: (value) => (value == null || value.trim().isEmpty) ? 'Vul een titel in' : null,
              ),
              const SizedBox(height: 8),
              TextFormField(
                controller: _description,
                decoration: const InputDecoration(labelText: 'Omschrijving (optioneel)'),
                maxLines: 4,
                maxLength: 5000,
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  Expanded(child: Text('Onderdelen', style: Theme.of(context).textTheme.titleSmall)),
                  TextButton.icon(onPressed: _addPart, icon: const Icon(Icons.add), label: const Text('Onderdeel')),
                ],
              ),
              for (final (index, part) in _parts.indexed)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text('${part.quantity} × ${part.description}'),
                  subtitle: Text([part.brand, part.partNumber].whereType<String>().where((s) => s.isNotEmpty).join(' · ')),
                  trailing: IconButton(
                    tooltip: 'Verwijderen',
                    icon: const Icon(Icons.close),
                    onPressed: () => setState(() => _parts.removeAt(index)),
                  ),
                ),
              const SizedBox(height: 16),
              if (_error != null) ...[
                ErrorText(message: _error!),
                const SizedBox(height: 16),
              ],
              FilledButton(
                onPressed: _busy ? null : _submit,
                child: _busy ? const ButtonProgress() : const Text('Opslaan'),
              ),
              const SizedBox(height: 8),
              Text(
                widget.garageId == null
                    ? 'Je registreert dit als eigenaar. Het wordt getoond als "niet geverifieerd" tot er een factuur '
                        'of werkorder bij komt.'
                    : 'Je registreert dit namens je garage.',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PartDialog extends StatefulWidget {
  const _PartDialog();

  @override
  State<_PartDialog> createState() => _PartDialogState();
}

class _PartDialogState extends State<_PartDialog> {
  final _formKey = GlobalKey<FormState>();
  final _description = TextEditingController();
  final _brand = TextEditingController();
  final _partNumber = TextEditingController();
  final _quantity = TextEditingController(text: '1');

  @override
  void dispose() {
    for (final controller in [_description, _brand, _partNumber, _quantity]) {
      controller.dispose();
    }
    super.dispose();
  }

  void _save() {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    Navigator.pop(
      context,
      NewPart(
        description: _description.text.trim(),
        brand: _brand.text.trim(),
        partNumber: _partNumber.text.trim(),
        quantity: int.parse(_quantity.text),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Onderdeel'),
      content: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextFormField(
              controller: _description,
              decoration: const InputDecoration(labelText: 'Omschrijving'),
              validator: (value) => (value == null || value.trim().isEmpty) ? 'Verplicht veld' : null,
            ),
            const SizedBox(height: 12),
            TextFormField(controller: _brand, decoration: const InputDecoration(labelText: 'Merk (optioneel)')),
            const SizedBox(height: 12),
            TextFormField(
              controller: _partNumber,
              decoration: const InputDecoration(labelText: 'Onderdeelnummer (optioneel)'),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _quantity,
              decoration: const InputDecoration(labelText: 'Aantal'),
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              validator: (value) {
                final quantity = int.tryParse(value ?? '');
                return (quantity == null || quantity < 1 || quantity > 999) ? 'Tussen 1 en 999' : null;
              },
            ),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Annuleren')),
        FilledButton(onPressed: _save, child: const Text('Toevoegen')),
      ],
    );
  }
}
