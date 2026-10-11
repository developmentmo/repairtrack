import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format/formatters.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/widgets/async_value_view.dart';
import '../../../core/widgets/form_widgets.dart';
import '../application/share_providers.dart';
import '../data/share_api.dart';
import '../domain/share.dart';

String shareStatusLabel(AppLocalizations l, ShareStatus status) => switch (status) {
      ShareStatus.active => l.shareStatusActive,
      ShareStatus.expired => l.shareStatusExpired,
      ShareStatus.revoked => l.shareStatusRevoked,
      ShareStatus.ownerChanged => l.shareStatusOwnerChanged,
      ShareStatus.unknown => l.commonUnknown,
    };

/// Owner: share the history with a buyer, insurer or garage through a link that needs no account.
class ShareVehicleScreen extends ConsumerWidget {
  const ShareVehicleScreen({super.key, required this.vehicleId});

  final String vehicleId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = context.l10n;
    final shares = ref.watch(vehicleSharesProvider(vehicleId));
    return Scaffold(
      appBar: AppBar(title: Text(l.shareVehicleTitle)),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _create(context, ref),
        icon: const Icon(Icons.link),
        label: Text(l.shareNewLink),
      ),
      body: AsyncValueView(
        value: shares,
        onRetry: () => ref.invalidate(vehicleSharesProvider(vehicleId)),
        data: (list) => ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
          children: [
            InfoBanner(message: l.shareVehicleInfo),
            const SizedBox(height: 16),
            if (list.isEmpty) Text(l.shareVehicleEmpty),
            for (final share in list) _ShareTile(vehicleId: vehicleId, share: share),
          ],
        ),
      ),
    );
  }

  Future<void> _create(BuildContext context, WidgetRef ref) async {
    final options = await showDialog<_NewShareOptions>(context: context, builder: (context) => const _NewShareDialog());
    if (options == null || !context.mounted) {
      return;
    }
    final l = context.l10n;
    final messenger = ScaffoldMessenger.of(context);
    try {
      final created = await ref.read(shareApiProvider).create(
            vehicleId,
            validDays: options.validDays,
            includeDocuments: options.includeDocuments,
          );
      ref.invalidate(vehicleSharesProvider(vehicleId));
      if (context.mounted) {
        await showDialog<void>(context: context, builder: (context) => _CreatedLinkDialog(created: created));
      }
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(userMessage(l, e))));
    }
  }
}

class _ShareTile extends ConsumerWidget {
  const _ShareTile({required this.vehicleId, required this.share});

  final String vehicleId;
  final VehicleShare share;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = context.l10n;
    final details = [
      shareStatusLabel(l, share.status),
      l.shareValidUntil(formatDate(share.expiresAt.toLocal())),
      if (share.includeDocuments) l.shareWithDocuments,
      l.shareViewCount(share.accessCount),
    ].join(' · ');
    return Card(
      child: ListTile(
        leading: Icon(share.isActive ? Icons.link : Icons.link_off),
        title: Text(l.shareLinkFrom(formatDateTime(share.createdAt))),
        subtitle: Text(details),
        trailing: share.isActive
            ? TextButton(onPressed: () => _revoke(context, ref), child: Text(l.shareRevoke))
            : null,
      ),
    );
  }

  Future<void> _revoke(BuildContext context, WidgetRef ref) async {
    final l = context.l10n;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l.shareRevokeTitle),
        content: Text(l.shareRevokeText),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l.commonCancel)),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(l.shareRevoke)),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) {
      return;
    }
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(shareApiProvider).revoke(share.id);
      ref.invalidate(vehicleSharesProvider(vehicleId));
      messenger.showSnackBar(SnackBar(content: Text(l.shareRevoked)));
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(userMessage(l, e))));
    }
  }
}

class _NewShareOptions {
  const _NewShareOptions(this.validDays, this.includeDocuments);

  final int validDays;
  final bool includeDocuments;
}

class _NewShareDialog extends StatefulWidget {
  const _NewShareDialog();

  @override
  State<_NewShareDialog> createState() => _NewShareDialogState();
}

class _NewShareDialogState extends State<_NewShareDialog> {
  int _validDays = 30;
  bool _includeDocuments = false;

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return AlertDialog(
      title: Text(l.shareNewLink),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          DropdownButtonFormField<int>(
            initialValue: _validDays,
            decoration: InputDecoration(labelText: l.shareValidFor),
            items: [
              for (final days in shareValidityOptions)
                DropdownMenuItem(value: days, child: Text(days == 365 ? l.shareValidityYear : l.shareValidityDays(days))),
            ],
            onChanged: (days) => setState(() => _validDays = days ?? _validDays),
          ),
          const SizedBox(height: 8),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            value: _includeDocuments,
            onChanged: (value) => setState(() => _includeDocuments = value),
            title: Text(l.shareIncludeDocuments),
            subtitle: Text(l.shareIncludeDocumentsHelp),
          ),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(l.commonCancel)),
        FilledButton(
          onPressed: () => Navigator.pop(context, _NewShareOptions(_validDays, _includeDocuments)),
          child: Text(l.shareCreateLink),
        ),
      ],
    );
  }
}

/// The link is shown only once: RepairTrack stores only a fingerprint of it.
class _CreatedLinkDialog extends StatelessWidget {
  const _CreatedLinkDialog({required this.created});

  final CreatedShare created;

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return AlertDialog(
      title: Text(l.shareLinkCreated),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SelectableText(created.url),
          const SizedBox(height: 12),
          Text(l.shareCopyNow),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(l.commonClose)),
        FilledButton.icon(
          onPressed: () async {
            await Clipboard.setData(ClipboardData(text: created.url));
            if (context.mounted) {
              ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l.shareLinkCopied)));
              Navigator.pop(context);
            }
          },
          icon: const Icon(Icons.copy),
          label: Text(l.shareCopy),
        ),
      ],
    );
  }
}
