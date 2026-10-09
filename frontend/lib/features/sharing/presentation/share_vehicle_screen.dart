import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/widgets/async_value_view.dart';
import '../../../core/widgets/form_widgets.dart';
import '../application/share_providers.dart';
import '../data/share_api.dart';
import '../domain/share.dart';

String shareStatusLabel(ShareStatus status) => switch (status) {
      ShareStatus.active => 'Actief',
      ShareStatus.expired => 'Verlopen',
      ShareStatus.revoked => 'Ingetrokken',
      ShareStatus.ownerChanged => 'Vervallen (andere eigenaar)',
      ShareStatus.unknown => 'Onbekend',
    };

/// Owner: share the history with a buyer, insurer or garage through a link that needs no account.
class ShareVehicleScreen extends ConsumerWidget {
  const ShareVehicleScreen({super.key, required this.vehicleId});

  final String vehicleId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final shares = ref.watch(vehicleSharesProvider(vehicleId));
    return Scaffold(
      appBar: AppBar(title: const Text('Voertuig delen')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _create(context, ref),
        icon: const Icon(Icons.link),
        label: const Text('Nieuwe link'),
      ),
      body: AsyncValueView(
        value: shares,
        onRetry: () => ref.invalidate(vehicleSharesProvider(vehicleId)),
        data: (list) => ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
          children: [
            const InfoBanner(
              message: 'Met een link kan iedereen de onderhoudshistorie bekijken, zonder account. Jouw naam, '
                  'e-mailadres en het VIN worden nooit getoond. Een link werkt niet meer na het intrekken, na de '
                  'vervaldatum of zodra je het voertuig verkoopt.',
            ),
            const SizedBox(height: 16),
            if (list.isEmpty) const Text('Je hebt nog geen links gemaakt.'),
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
      messenger.showSnackBar(SnackBar(content: Text(userMessage(e))));
    }
  }
}

class _ShareTile extends ConsumerWidget {
  const _ShareTile({required this.vehicleId, required this.share});

  final String vehicleId;
  final VehicleShare share;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final details = [
      shareStatusLabel(share.status),
      'geldig tot ${formatDate(share.expiresAt.toLocal())}',
      if (share.includeDocuments) 'met documenten',
      '${share.accessCount}× bekeken',
    ].join(' · ');
    return Card(
      child: ListTile(
        leading: Icon(share.isActive ? Icons.link : Icons.link_off),
        title: Text('Link van ${formatDateTime(share.createdAt)}'),
        subtitle: Text(details),
        trailing: share.isActive
            ? TextButton(onPressed: () => _revoke(context, ref), child: const Text('Intrekken'))
            : null,
      ),
    );
  }

  Future<void> _revoke(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Link intrekken?'),
        content: const Text('Wie de link heeft, kan de historie daarna niet meer bekijken.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Annuleren')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Intrekken')),
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
      messenger.showSnackBar(const SnackBar(content: Text('Link ingetrokken.')));
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(userMessage(e))));
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
    return AlertDialog(
      title: const Text('Nieuwe link'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          DropdownButtonFormField<int>(
            initialValue: _validDays,
            decoration: const InputDecoration(labelText: 'Geldig'),
            items: [
              for (final days in shareValidityOptions)
                DropdownMenuItem(value: days, child: Text(days == 365 ? '1 jaar' : '$days dagen')),
            ],
            onChanged: (days) => setState(() => _validDays = days ?? _validDays),
          ),
          const SizedBox(height: 8),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            value: _includeDocuments,
            onChanged: (value) => setState(() => _includeDocuments = value),
            title: const Text('Documenten meedelen'),
            subtitle: const Text('Facturen en werkorders kunnen dan worden geopend. Controleer of daar geen '
                'persoonsgegevens op staan.'),
          ),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Annuleren')),
        FilledButton(
          onPressed: () => Navigator.pop(context, _NewShareOptions(_validDays, _includeDocuments)),
          child: const Text('Link maken'),
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
    return AlertDialog(
      title: const Text('Link gemaakt'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SelectableText(created.url),
          const SizedBox(height: 12),
          const Text(
            'Kopieer de link nu. Hij wordt maar één keer getoond; daarna kun je hem alleen nog intrekken.',
          ),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Sluiten')),
        FilledButton.icon(
          onPressed: () async {
            await Clipboard.setData(ClipboardData(text: created.url));
            if (context.mounted) {
              ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Link gekopieerd.')));
              Navigator.pop(context);
            }
          },
          icon: const Icon(Icons.copy),
          label: const Text('Kopiëren'),
        ),
      ],
    );
  }
}
