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
    final l = context.l10n;
    final repair = ref.watch(repairProvider(repairId));
    final loaded = repair.hasValue ? repair.requireValue : null;
    return Scaffold(
      appBar: AppBar(
        title: Text(l.repairDetailTitle),
        actions: [
          if (loaded != null && (loaded.canCorrect || loaded.canVoid))
            PopupMenuButton<_Action>(
              onSelected: (action) => _onAction(context, ref, loaded, action),
              itemBuilder: (context) => [
                if (loaded.canCorrect) PopupMenuItem(value: _Action.correct, child: Text(l.repairCorrect)),
                if (loaded.canCorrect) PopupMenuItem(value: _Action.addPart, child: Text(l.repairAddPart)),
                if (loaded.canVoid) PopupMenuItem(value: _Action.voidRecord, child: Text(l.commonVoid)),
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
                message: '${repair.voidedAt == null ? l.repairVoided : l.repairVoidedOn(formatDateTime(repair.voidedAt!))}'
                    '${repair.voidReason == null ? '' : ': ${repair.voidReason}'}. '
                    '${l.repairVoidedStillVisible}',
              ),
              const SizedBox(height: 16),
            ],
            if (repair.enteredDuringRevokedOwnership) ...[
              InfoBanner(
                warning: true,
                icon: Icons.gavel,
                message: '${l.repairRevokedOwnership}.'
                    '${repair.canVoid ? ' ${l.repairRevokedOwnershipCanVoid}' : ''}',
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
              title: l.repairDetailsSection,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  DetailRow(label: l.repairDetailType, value: eventTypeLabel(l, repair.eventType)),
                  if (repair.garage != null)
                    DetailRow(
                      label: l.commonGarage,
                      value: [repair.garage!.name, repair.garage!.city].whereType<String>().join(', '),
                    ),
                  DetailRow(label: l.repairDate, value: formatDate(repair.eventDate)),
                  DetailRow(label: l.commonMileage, value: formatKm(repair.mileage)),
                  DetailRow(label: l.repairSource, value: sourceTypeLabel(l, repair.sourceType)),
                  DetailRow(label: l.repairRecordedAt, value: formatDateTime(repair.createdAt)),
                  if (repair.description != null && repair.description!.isNotEmpty)
                    DetailRow(label: l.repairDescriptionLabel, value: repair.description!),
                ],
              ),
            ),
            if (repair.parts.isNotEmpty)
              SectionCard(
                title: l.repairParts,
                child: Column(
                  children: [
                    for (final part in repair.parts)
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: const IconBadge(icon: Icons.settings_outlined, tone: Tone.neutral, size: 36),
                        title: Text(part.description),
                        subtitle: Text([part.brand, part.partNumber].whereType<String>().join(' · ')),
                        trailing: Text(l.repairPartQuantity(part.quantity)),
                      ),
                  ],
                ),
              ),
            if (repair.corrections.isNotEmpty)
              SectionCard(
                title: l.repairCorrections,
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
    final l = context.l10n;
    switch (action) {
      case _Action.correct:
        await context.push(Routes.correctRepair(repair.id));
      case _Action.addPart:
        final part = await showDialog<NewPart>(context: context, builder: (context) => const PartDialog());
        if (part != null && context.mounted) {
          await _run(context, ref, repair, () => ref.read(repairApiProvider).addParts(repair.id, [part]),
              l.repairPartAdded);
        }
      case _Action.voidRecord:
        final reason = await showDialog<String>(context: context, builder: (context) => const _VoidDialog());
        if (reason != null && context.mounted) {
          await _run(context, ref, repair, () => ref.read(repairApiProvider).voidRepair(repair.id, reason),
              l.repairVoidSuccess);
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
    final l = context.l10n;
    final messenger = ScaffoldMessenger.of(context);
    try {
      await call();
      ref
        ..invalidate(repairProvider(repair.id))
        ..invalidate(vehicleRepairsProvider(repair.vehicleId))
        ..invalidate(mileageHistoryProvider(repair.vehicleId));
      messenger.showSnackBar(SnackBar(content: Text(success)));
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(userMessage(l, e))));
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
    final l = context.l10n;
    return AlertDialog(
      title: Text(l.commonVoid),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(l.repairVoidDialogText),
          const SizedBox(height: 12),
          TextField(
            controller: _reason,
            decoration: InputDecoration(labelText: l.repairVoidReason, hintText: l.repairVoidReasonHint),
            maxLength: 500,
            onChanged: (_) => setState(() {}),
          ),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(l.commonCancel)),
        FilledButton(
          onPressed: _reason.text.trim().isEmpty ? null : () => Navigator.pop(context, _reason.text.trim()),
          child: Text(l.commonVoid),
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
    final l = context.l10n;
    final by = correction.correctedByGarage?.name ?? l.repairCorrectedByOwner;
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(
        '${correctionFieldLabel(l, correction.field)}: '
        '${correctionValueLabel(l, correction.field, correction.originalValue)} → '
        '${correctionValueLabel(l, correction.field, correction.correctedValue)}',
      ),
      subtitle: Text('${correction.reason}\n${l.repairCorrectedBy(by, formatDateTime(correction.correctedAt))}'),
      isThreeLine: true,
    );
  }
}
