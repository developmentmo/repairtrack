import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/format/formatters.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/widgets/async_value_view.dart';
import '../../../core/widgets/form_widgets.dart';
import '../application/dispute_providers.dart';
import '../data/dispute_api.dart';
import '../domain/dispute.dart';

/// SYSTEM_ADMIN: ownership disputes. Both sides, their files, and the final decision.
class AdminDisputesTab extends ConsumerStatefulWidget {
  const AdminDisputesTab({super.key});

  @override
  ConsumerState<AdminDisputesTab> createState() => _AdminDisputesTabState();
}

class _AdminDisputesTabState extends ConsumerState<AdminDisputesTab> {
  bool _undecided = true;

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final disputes = ref.watch(adminDisputesProvider(_undecided));
    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(adminDisputesProvider(_undecided));
        await ref.read(adminDisputesProvider(_undecided).future);
      },
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SegmentedButton<bool>(
            segments: [
              ButtonSegment(value: true, label: Text(l.adminDisputesFilterOpen)),
              ButtonSegment(value: false, label: Text(l.adminDisputesFilterDecided)),
            ],
            selected: {_undecided},
            onSelectionChanged: (selection) => setState(() => _undecided = selection.first),
          ),
          const SizedBox(height: 12),
          AsyncValueView(
            value: disputes,
            onRetry: () => ref.invalidate(adminDisputesProvider(_undecided)),
            data: (list) => Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (list.isEmpty) Text(_undecided ? l.adminDisputesEmptyOpen : l.adminDisputesEmptyDecided),
                for (final dispute in list) _AdminDisputeCard(dispute: dispute),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _AdminDisputeCard extends ConsumerWidget {
  const _AdminDisputeCard({required this.dispute});

  final AdminDispute dispute;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = context.l10n;
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(dispute.vehicle?.displayName ?? l.commonVehicle, style: theme.textTheme.titleMedium),
                ),
                Text(disputeStatusLabel(l, dispute.status)),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              l.adminDisputeSubmitted(formatDateTime(dispute.createdAt), formatDateTime(dispute.responseDeadline)),
              style: theme.textTheme.bodySmall,
            ),
            const SizedBox(height: 8),
            Text(l.adminDisputeClaimant(dispute.claimant?.displayName ?? '-'), style: theme.textTheme.labelLarge),
            Text(dispute.claimantStatement),
            const SizedBox(height: 8),
            Text(l.adminDisputeCurrentOwner(dispute.owner?.displayName ?? '-'), style: theme.textTheme.labelLarge),
            Text(dispute.ownerStatement ?? l.adminDisputeNoOwnerResponse),
            if (dispute.evidence.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(l.adminDisputeFiles, style: theme.textTheme.labelLarge),
              for (final file in dispute.evidence)
                ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.description_outlined),
                  title: Text(file.fileName),
                  subtitle: Text('${file.party == DisputeParty.claimant ? l.disputePartyClaimant : l.commonOwner} · '
                      '${formatDateTime(file.uploadedAt)}'),
                  onTap: () => _open(context, ref, file),
                ),
            ],
            if (dispute.status.isDecided) ...[
              const SizedBox(height: 8),
              Text(
                dispute.status == DisputeStatus.upheld
                    ? (dispute.newOwnerSince == null
                        ? l.adminDisputeUpheld
                        : l.adminDisputeUpheldSince(formatDate(dispute.newOwnerSince!)))
                    : l.adminDisputeRejected,
                style: theme.textTheme.labelLarge,
              ),
              if (dispute.decisionNote != null) Text(dispute.decisionNote!),
            ] else if (!dispute.reviewable) ...[
              const SizedBox(height: 8),
              Text(l.adminDisputeOwnerCanStillRespond, style: theme.textTheme.bodySmall),
            ] else ...[
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: [
                  FilledButton(
                    onPressed: () => _decide(context, ref, uphold: true),
                    child: Text(l.commonUphold),
                  ),
                  OutlinedButton(
                    onPressed: () => _decide(context, ref, uphold: false),
                    child: Text(l.commonReject),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _open(BuildContext context, WidgetRef ref, DisputeEvidence file) async {
    final l = context.l10n;
    final messenger = ScaffoldMessenger.of(context);
    try {
      final url = await ref.read(disputeApiProvider).evidenceUrl(dispute.id, file.id);
      if (!await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication)) {
        messenger.showSnackBar(SnackBar(content: Text(l.disputeFileOpenFailed)));
      }
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(userMessage(l, e))));
    }
  }

  Future<void> _decide(BuildContext context, WidgetRef ref, {required bool uphold}) async {
    final decision = await showDialog<_Decision>(
      context: context,
      builder: (context) => _DecisionDialog(uphold: uphold),
    );
    if (decision == null || !context.mounted) {
      return;
    }
    final l = context.l10n;
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(disputeApiProvider).decide(dispute.id,
          uphold: uphold, note: decision.note, newOwnerSince: decision.newOwnerSince);
      ref
        ..invalidate(adminDisputesProvider(true))
        ..invalidate(adminDisputesProvider(false));
      messenger.showSnackBar(SnackBar(content: Text(uphold ? l.adminDisputeUpheldSnack : l.adminDisputeRejectedSnack)));
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(userMessage(l, e))));
    }
  }
}

class _Decision {
  const _Decision(this.note, this.newOwnerSince);

  final String note;
  final DateTime? newOwnerSince;
}

class _DecisionDialog extends StatefulWidget {
  const _DecisionDialog({required this.uphold});

  final bool uphold;

  @override
  State<_DecisionDialog> createState() => _DecisionDialogState();
}

class _DecisionDialogState extends State<_DecisionDialog> {
  final _note = TextEditingController();
  DateTime? _since;
  String? _error;

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  Future<void> _pickSince() async {
    final picked = await showDatePicker(
      context: context,
      firstDate: DateTime(1950),
      lastDate: today(),
      initialDate: _since ?? today(),
      helpText: context.l10n.adminDisputeOwnerSince,
    );
    if (picked != null) {
      setState(() => _since = picked);
    }
  }

  void _confirm() {
    if (_note.text.trim().length < 10) {
      setState(() => _error = context.l10n.adminDisputeNoteTooShort);
      return;
    }
    Navigator.of(context).pop(_Decision(_note.text.trim(), _since));
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return AlertDialog(
      title: Text(widget.uphold ? l.adminDisputeUpholdTitle : l.adminDisputeRejectTitle),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(widget.uphold ? l.adminDisputeUpholdText : l.adminDisputeRejectText),
            const SizedBox(height: 12),
            TextField(
              controller: _note,
              decoration: InputDecoration(
                labelText: l.commonExplanation,
                helperText: l.adminDisputeNoteHelper,
                helperMaxLines: 3,
                alignLabelWithHint: true,
              ),
              minLines: 3,
              maxLines: 6,
              maxLength: 2000,
            ),
            if (widget.uphold) ...[
              const SizedBox(height: 8),
              OutlinedButton.icon(
                onPressed: _pickSince,
                icon: const Icon(Icons.event),
                label: Text(_since == null ? l.adminDisputeNewOwnerSinceToday : l.adminDisputeNewOwnerSince(formatDate(_since!))),
              ),
            ],
            if (_error != null) ...[
              const SizedBox(height: 12),
              ErrorText(message: _error!),
            ],
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: Text(l.commonCancel)),
        FilledButton(onPressed: _confirm, child: Text(widget.uphold ? l.commonUphold : l.commonReject)),
      ],
    );
  }
}
