import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/widgets/async_value_view.dart';
import '../../../core/widgets/form_widgets.dart';
import '../application/dispute_providers.dart';
import '../data/dispute_api.dart';
import '../domain/dispute.dart';
import 'evidence_picker.dart';

/// Disputes the user filed, and disputes about their ownership. Emails about disputes link here.
class MyDisputesScreen extends ConsumerWidget {
  const MyDisputesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final disputes = ref.watch(myDisputesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Geschillen over eigendom')),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(myDisputesProvider);
          await ref.read(myDisputesProvider.future);
        },
        child: AsyncValueView(
          value: disputes,
          onRetry: () => ref.invalidate(myDisputesProvider),
          data: (list) => ListView(
            padding: const EdgeInsets.all(16),
            children: [
              if (list.isEmpty) const Text('Je hebt geen geschillen.'),
              for (final dispute in list) DisputeCard(dispute: dispute),
            ],
          ),
        ),
      ),
    );
  }
}

class DisputeCard extends ConsumerStatefulWidget {
  const DisputeCard({super.key, required this.dispute});

  final PartyDispute dispute;

  @override
  ConsumerState<DisputeCard> createState() => _DisputeCardState();
}

class _DisputeCardState extends ConsumerState<DisputeCard> {
  bool _busy = false;

  void _show(String message) => ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));

  Future<void> _respond() async {
    final sent = await showDialog<bool>(
      context: context,
      builder: (context) => _RespondDialog(disputeId: widget.dispute.id),
    );
    if (sent == true && mounted) {
      ref.invalidate(myDisputesProvider);
      _show('Je reactie is verstuurd. RepairTrack beoordeelt het geschil.');
    }
  }

  Future<void> _addEvidence() async {
    final file = await pickEvidenceFile(_show);
    if (file == null || !mounted) {
      return;
    }
    setState(() => _busy = true);
    try {
      await ref.read(disputeApiProvider).addEvidence(widget.dispute.id, file);
      ref.invalidate(myDisputesProvider);
      _show('Bestand toegevoegd.');
    } catch (e) {
      _show(userMessage(e));
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final dispute = widget.dispute;
    final theme = Theme.of(context);
    final owner = dispute.role == DisputeParty.owner;
    final vehicle = dispute.vehicle?.displayName ?? 'Voertuig';
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Text(vehicle, style: theme.textTheme.titleMedium)),
                Text(disputeStatusLabel(dispute.status)),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              owner ? 'Iemand betwist dat jij de eigenaar bent.' : 'Jij betwist het eigendom van dit voertuig.',
              style: theme.textTheme.bodySmall,
            ),
            Text('Ingediend op ${formatDate(dispute.createdAt.toLocal())}', style: theme.textTheme.bodySmall),
            if (dispute.status == DisputeStatus.open)
              Text(
                owner
                    ? 'Reageren kan tot ${formatDateTime(dispute.responseDeadline)}.'
                    : 'De eigenaar kan reageren tot ${formatDateTime(dispute.responseDeadline)}.',
                style: theme.textTheme.bodySmall,
              ),
            if (owner && dispute.canRespond) ...[
              const SizedBox(height: 8),
              const InfoBanner(
                warning: true,
                icon: Icons.gavel,
                message: 'Geef je reactie en voeg bewijs toe dat je de eigenaar bent. Zolang het geschil loopt, '
                    'kun je geen nieuwe deellinks maken.',
              ),
            ],
            if (dispute.myStatement != null) ...[
              const SizedBox(height: 8),
              Text(owner ? 'Jouw reactie' : 'Jouw toelichting', style: theme.textTheme.labelLarge),
              Text(dispute.myStatement!),
            ],
            if (dispute.myEvidence.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text('Jouw bestanden', style: theme.textTheme.labelLarge),
              for (final file in dispute.myEvidence) Text('• ${file.fileName}'),
            ],
            if (dispute.status.isDecided) ...[
              const SizedBox(height: 8),
              Text(_outcome(dispute), style: theme.textTheme.labelLarge),
              if (dispute.decisionNote != null) Text(dispute.decisionNote!),
            ],
            if (dispute.canRespond || dispute.canAddEvidence) ...[
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  if (dispute.canRespond)
                    FilledButton(
                      key: Key('dispute-respond-${dispute.id}'),
                      onPressed: _busy ? null : _respond,
                      child: const Text('Reageren'),
                    ),
                  if (dispute.canAddEvidence)
                    OutlinedButton(
                      onPressed: _busy ? null : _addEvidence,
                      child: _busy ? const ButtonProgress() : const Text('Bestand toevoegen'),
                    ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  static String _outcome(PartyDispute dispute) {
    final owner = dispute.role == DisputeParty.owner;
    final upheld = dispute.status == DisputeStatus.upheld;
    if (upheld) {
      return owner
          ? 'Uitkomst: je eigendom is ingetrokken.'
          : 'Uitkomst: je bent nu de eigenaar'
              '${dispute.newOwnerSince == null ? '' : ' (vanaf ${formatDate(dispute.newOwnerSince!)})'}.';
    }
    return owner ? 'Uitkomst: je blijft de eigenaar.' : 'Uitkomst: je geschil is afgewezen.';
  }
}

class _RespondDialog extends ConsumerStatefulWidget {
  const _RespondDialog({required this.disputeId});

  final String disputeId;

  @override
  ConsumerState<_RespondDialog> createState() => _RespondDialogState();
}

class _RespondDialogState extends ConsumerState<_RespondDialog> {
  final _statement = TextEditingController();
  EvidenceFile? _file;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _statement.dispose();
    super.dispose();
  }

  Future<void> _pick() async {
    final file = await pickEvidenceFile((message) => setState(() => _error = message));
    if (file != null && mounted) {
      setState(() => _file = file);
    }
  }

  Future<void> _send() async {
    if (_statement.text.trim().length < 10) {
      setState(() => _error = 'Schrijf ten minste een paar woorden (10 tekens).');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(disputeApiProvider).respond(widget.disputeId, statement: _statement.text.trim(), file: _file);
      if (mounted) {
        Navigator.of(context).pop(true);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = userMessage(e);
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Reageren op het geschil'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text('Je kunt één keer reageren. Leg uit waarom de auto van jou is.'),
            const SizedBox(height: 12),
            TextField(
              key: const Key('dispute-response'),
              controller: _statement,
              decoration: const InputDecoration(labelText: 'Jouw reactie', alignLabelWithHint: true),
              minLines: 3,
              maxLines: 6,
              maxLength: 2000,
            ),
            const SizedBox(height: 8),
            const Text(evidenceHint),
            const SizedBox(height: 8),
            EvidenceFileButton(file: _file, onPressed: _busy ? null : _pick, label: 'Bestand toevoegen (optioneel)'),
            if (_error != null) ...[
              const SizedBox(height: 12),
              ErrorText(message: _error!),
            ],
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: _busy ? null : () => Navigator.of(context).pop(false),
          child: const Text('Annuleren'),
        ),
        FilledButton(
          onPressed: _busy ? null : _send,
          child: _busy ? const ButtonProgress() : const Text('Versturen'),
        ),
      ],
    );
  }
}
