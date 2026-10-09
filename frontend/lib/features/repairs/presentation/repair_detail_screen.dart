import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/async_value_view.dart';
import '../../../core/widgets/brand_widgets.dart';
import '../../../core/widgets/form_widgets.dart';
import '../../documents/presentation/repair_documents_section.dart';
import '../application/repair_providers.dart';
import '../data/repair_api.dart';
import '../domain/repair.dart';
import 'part_dialog.dart';
import 'repair_labels.dart';

enum _Action { correct, addPart, voidRecord }

class RepairDetailScreen extends ConsumerWidget {
  const RepairDetailScreen({super.key, required this.repairId});

  final String repairId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final repair = ref.watch(repairProvider(repairId));
    final loaded = repair.hasValue ? repair.requireValue : null;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Reparatie'),
        actions: [
          if (loaded != null && (loaded.canCorrect || loaded.canVoid))
            PopupMenuButton<_Action>(
              onSelected: (action) => _onAction(context, ref, loaded, action),
              itemBuilder: (context) => [
                if (loaded.canCorrect) const PopupMenuItem(value: _Action.correct, child: Text('Corrigeren')),
                if (loaded.canCorrect) const PopupMenuItem(value: _Action.addPart, child: Text('Onderdeel toevoegen')),
                if (loaded.canVoid) const PopupMenuItem(value: _Action.voidRecord, child: Text('Ongeldig verklaren')),
              ],
            ),
        ],
      ),
      body: ContentWidth(
        maxWidth: 900,
        child: AsyncValueView(
        value: repair,
        onRetry: () => ref.invalidate(repairProvider(repairId)),
        data: (repair) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            if (repair.isVoided) ...[
              InfoBanner(
                warning: true,
                icon: Icons.block,
                message: 'Ongeldig verklaard${repair.voidedAt == null ? '' : ' op ${formatDateTime(repair.voidedAt!)}'}'
                    '${repair.voidReason == null ? '' : ': ${repair.voidReason}'}. '
                    'De registratie blijft zichtbaar in de historie.',
              ),
              const SizedBox(height: 16),
            ],
            if (repair.enteredDuringRevokedOwnership) ...[
              InfoBanner(
                warning: true,
                icon: Icons.gavel,
                message: 'Ingevoerd door een eigenaar van wie het eigendom na een geschil is ingetrokken.'
                    '${repair.canVoid ? ' Je kunt deze registratie ongeldig verklaren als ze niet klopt.' : ''}',
              ),
              const SizedBox(height: 16),
            ],
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    EventTypeIcon(type: repair.eventType, size: 48),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(repair.title, style: Theme.of(context).textTheme.titleLarge),
                          Text(
                            '${formatDate(repair.eventDate)} · ${formatKm(repair.mileage)}',
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    VerificationBadge(status: repair.verificationStatus),
                  ],
                ),
              ),
            ),
            SectionCard(
              title: 'Details',
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  DetailRow(label: 'Type', value: eventTypeLabel(repair.eventType)),
                  if (repair.garage != null)
                    DetailRow(
                      label: 'Garage',
                      value: [repair.garage!.name, repair.garage!.city].whereType<String>().join(', '),
                    ),
                  DetailRow(label: 'Datum', value: formatDate(repair.eventDate)),
                  DetailRow(label: 'Kilometerstand', value: formatKm(repair.mileage)),
                  DetailRow(label: 'Bron', value: sourceTypeLabel(repair.sourceType)),
                  DetailRow(label: 'Vastgelegd op', value: formatDateTime(repair.createdAt)),
                  if (repair.description != null && repair.description!.isNotEmpty)
                    DetailRow(label: 'Beschrijving', value: repair.description!),
                ],
              ),
            ),
            if (repair.parts.isNotEmpty)
              SectionCard(
                title: 'Onderdelen',
                child: Column(
                  children: [
                    for (final part in repair.parts)
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: const IconBadge(icon: Icons.settings_outlined, tone: Tone.neutral, size: 36),
                        title: Text(part.description),
                        subtitle: Text([part.brand, part.partNumber].whereType<String>().join(' · ')),
                        trailing: Text(part.quantity == 1 ? '1 stuk' : '${part.quantity} stuks'),
                      ),
                  ],
                ),
              ),
            if (repair.corrections.isNotEmpty)
              SectionCard(
                title: 'Correcties',
                child: Column(
                  children: [for (final correction in repair.corrections) _CorrectionTile(correction: correction)],
                ),
              ),
            const SizedBox(height: 16),
            RepairDocumentsSection(repair: repair),
          ],
        ),
        ),
      ),
    );
  }

  Future<void> _onAction(BuildContext context, WidgetRef ref, Repair repair, _Action action) async {
    switch (action) {
      case _Action.correct:
        await context.push(Routes.correctRepair(repair.id));
      case _Action.addPart:
        final part = await showDialog<NewPart>(context: context, builder: (context) => const PartDialog());
        if (part != null && context.mounted) {
          await _run(context, ref, repair, () => ref.read(repairApiProvider).addParts(repair.id, [part]),
              'Onderdeel toegevoegd.');
        }
      case _Action.voidRecord:
        final reason = await showDialog<String>(context: context, builder: (context) => const _VoidDialog());
        if (reason != null && context.mounted) {
          await _run(context, ref, repair, () => ref.read(repairApiProvider).voidRepair(repair.id, reason),
              'Registratie ongeldig verklaard. Ze blijft zichtbaar in de historie.');
        }
    }
  }

  Future<void> _run(
    BuildContext context,
    WidgetRef ref,
    Repair repair,
    Future<Repair> Function() call,
    String success,
  ) async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      await call();
      ref
        ..invalidate(repairProvider(repair.id))
        ..invalidate(vehicleRepairsProvider(repair.vehicleId))
        ..invalidate(mileageHistoryProvider(repair.vehicleId));
      messenger.showSnackBar(SnackBar(content: Text(success)));
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(userMessage(e))));
    }
  }
}

/// Asks for the (mandatory) reason. Voiding never deletes: the record stays visible with this reason.
class _VoidDialog extends StatefulWidget {
  const _VoidDialog();

  @override
  State<_VoidDialog> createState() => _VoidDialogState();
}

class _VoidDialogState extends State<_VoidDialog> {
  final _reason = TextEditingController();

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Ongeldig verklaren'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text(
            'De registratie wordt niet verwijderd. Ze blijft zichtbaar als ongeldig, met de reden die je hier geeft.',
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _reason,
            decoration: const InputDecoration(labelText: 'Reden', hintText: 'Bijv. verkeerd voertuig'),
            maxLength: 500,
            onChanged: (_) => setState(() {}),
          ),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Annuleren')),
        FilledButton(
          onPressed: _reason.text.trim().isEmpty ? null : () => Navigator.pop(context, _reason.text.trim()),
          child: const Text('Ongeldig verklaren'),
        ),
      ],
    );
  }
}

class _CorrectionTile extends StatelessWidget {
  const _CorrectionTile({required this.correction});

  final Correction correction;

  @override
  Widget build(BuildContext context) {
    final by = correction.correctedByGarage?.name ?? 'eigenaar';
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(
        '${correctionFieldLabel(correction.field)}: '
        '${correctionValueLabel(correction.field, correction.originalValue)} → '
        '${correctionValueLabel(correction.field, correction.correctedValue)}',
      ),
      subtitle: Text('${correction.reason}\nDoor $by op ${formatDateTime(correction.correctedAt)}'),
      isThreeLine: true,
    );
  }
}
