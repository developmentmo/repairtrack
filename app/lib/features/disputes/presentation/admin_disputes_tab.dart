import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/format/formatters.dart';
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
            segments: const [
              ButtonSegment(value: true, label: Text('Open')),
              ButtonSegment(value: false, label: Text('Besloten')),
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
                if (list.isEmpty) Text(_undecided ? 'Er zijn geen open geschillen.' : 'Nog geen besluiten.'),
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
                  child: Text(dispute.vehicle?.displayName ?? 'Voertuig', style: theme.textTheme.titleMedium),
                ),
                Text(disputeStatusLabel(dispute.status)),
              ],
            ),
            const SizedBox(height: 4),
            Text('Ingediend ${formatDateTime(dispute.createdAt)} · reactietermijn tot '
                '${formatDateTime(dispute.responseDeadline)}', style: theme.textTheme.bodySmall),
            const SizedBox(height: 8),
            Text('Indiener: ${dispute.claimant?.displayName ?? '-'}', style: theme.textTheme.labelLarge),
            Text(dispute.claimantStatement),
            const SizedBox(height: 8),
            Text('Huidige eigenaar: ${dispute.owner?.displayName ?? '-'}', style: theme.textTheme.labelLarge),
            Text(dispute.ownerStatement ?? 'Heeft (nog) niet gereageerd.'),
            if (dispute.evidence.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text('Bestanden', style: theme.textTheme.labelLarge),
              for (final file in dispute.evidence)
                ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.description_outlined),
                  title: Text(file.fileName),
                  subtitle: Text('${file.party == DisputeParty.claimant ? 'Indiener' : 'Eigenaar'} · '
                      '${formatDateTime(file.uploadedAt)}'),
                  onTap: () => _open(context, ref, file),
                ),
            ],
            if (dispute.status.isDecided) ...[
              const SizedBox(height: 8),
              Text(
                dispute.status == DisputeStatus.upheld
                    ? 'Toegekend${dispute.newOwnerSince == null ? '' : ', eigenaar vanaf ${formatDate(dispute.newOwnerSince!)}'}'
                    : 'Afgewezen',
                style: theme.textTheme.labelLarge,
              ),
              if (dispute.decisionNote != null) Text(dispute.decisionNote!),
            ] else if (!dispute.reviewable) ...[
              const SizedBox(height: 8),
              Text('De eigenaar kan nog reageren; daarna kun je beslissen.', style: theme.textTheme.bodySmall),
            ] else ...[
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: [
                  FilledButton(
                    onPressed: () => _decide(context, ref, uphold: true),
                    child: const Text('Toekennen'),
                  ),
                  OutlinedButton(
                    onPressed: () => _decide(context, ref, uphold: false),
                    child: const Text('Afwijzen'),
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
    final messenger = ScaffoldMessenger.of(context);
    try {
      final url = await ref.read(disputeApiProvider).evidenceUrl(dispute.id, file.id);
      if (!await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication)) {
        messenger.showSnackBar(const SnackBar(content: Text('Het bestand kon niet worden geopend.')));
      }
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(userMessage(e))));
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
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(disputeApiProvider).decide(dispute.id,
          uphold: uphold, note: decision.note, newOwnerSince: decision.newOwnerSince);
      ref
        ..invalidate(adminDisputesProvider(true))
        ..invalidate(adminDisputesProvider(false));
      messenger.showSnackBar(SnackBar(content: Text(uphold ? 'Geschil toegekend.' : 'Geschil afgewezen.')));
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(userMessage(e))));
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
      helpText: 'Eigenaar sinds',
    );
    if (picked != null) {
      setState(() => _since = picked);
    }
  }

  void _confirm() {
    if (_note.text.trim().length < 10) {
      setState(() => _error = 'Geef een toelichting van ten minste 10 tekens.');
      return;
    }
    Navigator.of(context).pop(_Decision(_note.text.trim(), _since));
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.uphold ? 'Geschil toekennen' : 'Geschil afwijzen'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(widget.uphold
                ? 'Het eigendom van de huidige eigenaar wordt ingetrokken en de indiener wordt eigenaar. '
                    'Dit besluit is definitief.'
                : 'Het eigendom blijft ongewijzigd. Dit besluit is definitief.'),
            const SizedBox(height: 12),
            TextField(
              controller: _note,
              decoration: const InputDecoration(
                labelText: 'Toelichting',
                helperText: 'Beide partijen krijgen deze tekst per e-mail. Noem geen namen of gegevens van de ander.',
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
                label: Text(_since == null ? 'Nieuwe eigenaar sinds: vandaag' : 'Nieuwe eigenaar sinds: ${formatDate(_since!)}'),
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
        TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Annuleren')),
        FilledButton(onPressed: _confirm, child: Text(widget.uphold ? 'Toekennen' : 'Afwijzen')),
      ],
    );
  }
}
