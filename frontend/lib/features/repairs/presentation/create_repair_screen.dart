import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/form_widgets.dart';
import '../../garages/application/garage_providers.dart';
import '../application/repair_providers.dart';
import '../data/repair_api.dart';
import '../domain/repair.dart';
import 'part_dialog.dart';
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
    final part = await showDialog<NewPart>(context: context, builder: (context) => const PartDialog());
    if (part != null) {
      setState(() => _parts.add(part));
    }
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final l = context.l10n;
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
      if (widget.garageId != null) {
        ref.invalidate(garageVehiclesProvider(widget.garageId!));
      }
      if (!mounted) {
        return;
      }
      if (created.warnings.isNotEmpty) {
        await _showWarnings(l, created.warnings);
      }
      if (mounted) {
        // Show the saved record: documents can be added there ("confirm repair" step).
        context.pushReplacement(Routes.repair(created.id));
      }
    } catch (e) {
      if (mounted) {
        setState(() => _error = userMessage(l, e));
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _showWarnings(AppLocalizations l, List<MileageWarning> warnings) {
    return showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.speed),
        title: Text(l.createRepairWarningTitle),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            for (final warning in warnings) ...[Text(mileageWarningText(l, warning)), const SizedBox(height: 8)],
            Text(l.createRepairWarningCheck),
          ],
        ),
        actions: [FilledButton(onPressed: () => Navigator.pop(context), child: Text(l.repairUnderstood))],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(widget.garageId == null ? l.createRepairTitleOwner : l.createRepairTitleGarage)),
      body: CenteredForm(
        maxWidth: 560,
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              DropdownButtonFormField<RepairEventType>(
                initialValue: _eventType,
                decoration: InputDecoration(labelText: l.commonType),
                items: [
                  for (final type in RepairEventType.selectable)
                    DropdownMenuItem(value: type, child: Text(eventTypeLabel(l, type))),
                ],
                onChanged: (type) => setState(() => _eventType = type ?? _eventType),
              ),
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: _pickDate,
                icon: const Icon(Icons.event),
                label: Text(l.repairDateButton(formatDate(_eventDate))),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _mileage,
                decoration: InputDecoration(labelText: l.commonMileage, suffixText: 'km'),
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                validator: (value) {
                  final km = int.tryParse(value ?? '');
                  if (km == null) {
                    return l.repairMileageRequired;
                  }
                  return km > maxMileage ? l.repairMileageMax(formatThousands(maxMileage)) : null;
                },
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _title,
                decoration: InputDecoration(labelText: l.commonTitle, hintText: l.createRepairTitleHint),
                maxLength: 150,
                validator: (value) => (value == null || value.trim().isEmpty) ? l.repairTitleRequired : null,
              ),
              const SizedBox(height: 8),
              TextFormField(
                controller: _description,
                decoration: InputDecoration(labelText: l.createRepairDescriptionOptional),
                maxLines: 4,
                maxLength: 5000,
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  Expanded(child: Text(l.repairParts, style: Theme.of(context).textTheme.titleSmall)),
                  TextButton.icon(onPressed: _addPart, icon: const Icon(Icons.add), label: Text(l.createRepairAddPart)),
                ],
              ),
              for (final (index, part) in _parts.indexed)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text('${part.quantity} × ${part.description}'),
                  subtitle: Text([part.brand, part.partNumber].whereType<String>().where((s) => s.isNotEmpty).join(' · ')),
                  trailing: IconButton(
                    tooltip: l.commonDelete,
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
                child: _busy ? const ButtonProgress() : Text(l.commonSave),
              ),
              const SizedBox(height: 8),
              Text(
                widget.garageId == null ? l.createRepairOwnerNote : l.createRepairGarageNote,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
