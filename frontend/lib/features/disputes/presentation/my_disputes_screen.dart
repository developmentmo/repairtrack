import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format/formatters.dart';
import '../../../core/l10n/l10n.dart';
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
    final l = context.l10n;
    final disputes = ref.watch(myDisputesProvider);
    return Scaffold(
      appBar: AppBar(title: Text(l.myDisputesTitle)),
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
              if (list.isEmpty) Text(l.myDisputesEmpty),
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
    final l = context.l10n;
    final sent = await showDialog<bool>(
      context: context,
      builder: (context) => _RespondDialog(disputeId: widget.dispute.id),
    );
    if (sent == true && mounted) {
      ref.invalidate(myDisputesProvider);
      _show(l.myDisputeResponseSent);
    }
  }

  Future<void> _addEvidence() async {
    final l = context.l10n;
    final file = await pickEvidenceFile(l, _show);
    if (file == null || !mounted) {
      return;
    }
    setState(() => _busy = true);
    try {
      await ref.read(disputeApiProvider).addEvidence(widget.dispute.id, file);
      ref.invalidate(myDisputesProvider);
      _show(l.myDisputeFileAdded);
    } catch (e) {
      _show(userMessage(l, e));
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final dispute = widget.dispute;
    final theme = Theme.of(context);
    final owner = dispute.role == DisputeParty.owner;
    final vehicle = dispute.vehicle?.displayName ?? l.commonVehicle;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Text(vehicle, style: theme.textTheme.titleMedium)),
                Text(disputeStatusLabel(l, dispute.status)),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              owner ? l.myDisputeOwnerSubtitle : l.myDisputeClaimantSubtitle,
              style: theme.textTheme.bodySmall,
            ),
            Text(l.myDisputeSubmittedOn(formatDate(dispute.createdAt.toLocal())), style: theme.textTheme.bodySmall),
            if (dispute.status == DisputeStatus.open)
              Text(
                owner
                    ? l.myDisputeOwnerRespondUntil(formatDateTime(dispute.responseDeadline))
                    : l.myDisputeClaimantRespondUntil(formatDateTime(dispute.responseDeadline)),
                style: theme.textTheme.bodySmall,
              ),
            if (owner && dispute.canRespond) ...[
              const SizedBox(height: 8),
              InfoBanner(
                warning: true,
                icon: Icons.gavel,
                message: l.myDisputeOwnerBanner,
              ),
            ],
            if (dispute.myStatement != null) ...[
              const SizedBox(height: 8),
              Text(owner ? l.myDisputeYourResponse : l.myDisputeYourStatement, style: theme.textTheme.labelLarge),
              Text(dispute.myStatement!),
            ],
            if (dispute.myEvidence.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(l.myDisputeYourFiles, style: theme.textTheme.labelLarge),
              for (final file in dispute.myEvidence) Text('• ${file.fileName}'),
            ],
            if (dispute.status.isDecided) ...[
              const SizedBox(height: 8),
              Text(_outcome(l, dispute), style: theme.textTheme.labelLarge),
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
                      child: Text(l.myDisputeRespond),
                    ),
                  if (dispute.canAddEvidence)
                    OutlinedButton(
                      onPressed: _busy ? null : _addEvidence,
                      child: _busy ? const ButtonProgress() : Text(l.myDisputeAddFile),
                    ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  static String _outcome(AppLocalizations l, PartyDispute dispute) {
    final owner = dispute.role == DisputeParty.owner;
    final upheld = dispute.status == DisputeStatus.upheld;
    if (upheld) {
      if (owner) {
        return l.myDisputeOutcomeRevoked;
      }
      return dispute.newOwnerSince == null
          ? l.myDisputeOutcomeNowOwner
          : l.myDisputeOutcomeNowOwnerSince(formatDate(dispute.newOwnerSince!));
    }
    return owner ? l.myDisputeOutcomeStillOwner : l.myDisputeOutcomeRejected;
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
    final file = await pickEvidenceFile(context.l10n, (message) => setState(() => _error = message));
    if (file != null && mounted) {
      setState(() => _file = file);
    }
  }

  Future<void> _send() async {
    final l = context.l10n;
    if (_statement.text.trim().length < 10) {
      setState(() => _error = l.disputeStatementTooShort);
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
          _error = userMessage(l, e);
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return AlertDialog(
      title: Text(l.respondDisputeTitle),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l.respondDisputeIntro),
            const SizedBox(height: 12),
            TextField(
              key: const Key('dispute-response'),
              controller: _statement,
              decoration: InputDecoration(labelText: l.myDisputeYourResponse, alignLabelWithHint: true),
              minLines: 3,
              maxLines: 6,
              maxLength: 2000,
            ),
            const SizedBox(height: 8),
            Text(l.evidenceHint),
            const SizedBox(height: 8),
            EvidenceFileButton(file: _file, onPressed: _busy ? null : _pick, label: l.respondDisputeAddFileOptional),
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
          child: Text(l.commonCancel),
        ),
        FilledButton(
          onPressed: _busy ? null : _send,
          child: _busy ? const ButtonProgress() : Text(l.respondDisputeSend),
        ),
      ],
    );
  }
}
