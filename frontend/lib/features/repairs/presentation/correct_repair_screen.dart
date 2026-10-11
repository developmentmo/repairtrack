import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/widgets/async_value_view.dart';
import '../../../core/widgets/form_widgets.dart';
import '../application/repair_providers.dart';
import '../data/repair_api.dart';
import '../domain/repair.dart';
import 'repair_labels.dart';

/// Correct a record. Only changed fields are sent; the original values stay visible as corrections.
class CorrectRepairScreen extends ConsumerWidget {
  const CorrectRepairScreen({super.key, required this.repairId});

  final String repairId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final repair = ref.watch(repairProvider(repairId));
    return Scaffold(
      appBar: AppBar(title: Text(context.l10n.repairCorrect)),
      body: AsyncValueView(
        value: repair,
        onRetry: () => ref.invalidate(repairProvider(repairId)),
        data: (repair) => _CorrectionForm(repair: repair),
      ),
    );
  }
}

class _CorrectionForm extends ConsumerStatefulWidget {
  const _CorrectionForm({required this.repair});

  final Repair repair;

  @override
  ConsumerState<_CorrectionForm> createState() => _CorrectionFormState();
}

class _CorrectionFormState extends ConsumerState<_CorrectionForm> {
  final _formKey = GlobalKey<FormState>();
  late final _title = TextEditingController(text: widget.repair.title);
  late final _description = TextEditingController(text: widget.repair.description ?? '');
  late final _mileage = TextEditingController(text: widget.repair.mileage.toString());
  final _reason = TextEditingController();
  late RepairEventType _eventType =
      widget.repair.eventType == RepairEventType.unknown ? RepairEventType.other : widget.repair.eventType;
  late DateTime _eventDate = widget.repair.eventDate;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    for (final controller in [_title, _description, _mileage, _reason]) {
      controller.dispose();
    }
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

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final l = context.l10n;
    final correction = RepairCorrection.diff(
      widget.repair,
      reason: _reason.text.trim(),
      eventType: _eventType,
      eventDate: _eventDate,
      mileage: int.parse(_mileage.text),
      title: _title.text.trim(),
      description: _description.text.trim(),
    );
    if (correction == null) {
      setState(() => _error = l.correctRepairNoChanges);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final corrected = await ref.read(repairApiProvider).correct(widget.repair.id, correction);
      ref
        ..invalidate(repairProvider(widget.repair.id))
        ..invalidate(vehicleRepairsProvider(widget.repair.vehicleId))
        ..invalidate(mileageHistoryProvider(widget.repair.vehicleId));
      if (!mounted) {
        return;
      }
      if (corrected.warnings.isNotEmpty) {
        await showDialog<void>(
          context: context,
          builder: (context) => AlertDialog(
            icon: const Icon(Icons.speed),
            title: Text(l.correctRepairWarningTitle),
            content: Text(corrected.warnings.map((w) => mileageWarningText(l, w)).join('\n\n')),
            actions: [FilledButton(onPressed: () => Navigator.pop(context), child: Text(l.repairUnderstood))],
          ),
        );
      }
      if (mounted) {
        context.pop();
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

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return CenteredForm(
      maxWidth: 560,
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            InfoBanner(message: l.correctRepairInfo),
            const SizedBox(height: 16),
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
              validator: (value) => int.tryParse(value ?? '') == null ? l.repairMileageRequired : null,
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _title,
              decoration: InputDecoration(labelText: l.commonTitle),
              maxLength: 150,
              validator: (value) => (value == null || value.trim().isEmpty) ? l.repairTitleRequired : null,
            ),
            const SizedBox(height: 8),
            TextFormField(
              controller: _description,
              decoration: InputDecoration(labelText: l.commonDescription),
              maxLines: 4,
              maxLength: 5000,
            ),
            const SizedBox(height: 8),
            TextFormField(
              controller: _reason,
              decoration: InputDecoration(labelText: l.correctRepairReason, hintText: l.correctRepairReasonHint),
              maxLength: 500,
              validator: (value) => (value == null || value.trim().isEmpty) ? l.correctRepairReasonRequired : null,
            ),
            const SizedBox(height: 16),
            if (_error != null) ...[
              ErrorText(message: _error!),
              const SizedBox(height: 16),
            ],
            FilledButton(
              onPressed: _busy ? null : _submit,
              child: _busy ? const ButtonProgress() : Text(l.correctRepairSave),
            ),
          ],
        ),
      ),
    );
  }
}
