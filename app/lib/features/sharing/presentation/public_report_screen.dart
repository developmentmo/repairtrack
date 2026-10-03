import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/widgets/form_widgets.dart';
import '../../documents/domain/document.dart';
import '../../documents/presentation/repair_documents_section.dart';
import '../../garages/domain/garage.dart';
import '../../repairs/presentation/repair_labels.dart';
import '../application/share_providers.dart';
import '../data/share_api.dart';
import '../domain/public_report.dart';

/// The public vehicle history behind a share link (`/v/{token}`). No login, no private data.
class PublicReportScreen extends ConsumerWidget {
  const PublicReportScreen({super.key, required this.token});

  final String token;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final report = ref.watch(publicReportProvider(token));
    return Scaffold(
      appBar: AppBar(title: const Text('RepairTrack · Voertuighistorie'), automaticallyImplyLeading: false),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 760),
          child: _body(context, ref, report),
        ),
      ),
    );
  }

  Widget _body(BuildContext context, WidgetRef ref, AsyncValue<PublicReport> report) {
    if (report.hasValue) {
      return _Report(token: token, report: report.requireValue);
    }
    if (report.hasError) {
      final error = report.error;
      final invalidLink = error is ApiException && error.code == 'SHARE_NOT_FOUND';
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(invalidLink ? Icons.link_off : Icons.error_outline, size: 48),
              const SizedBox(height: 12),
              Text(
                invalidLink
                    ? 'Deze link is ongeldig of niet meer geldig. Vraag de eigenaar om een nieuwe link.'
                    : userMessage(error!),
                textAlign: TextAlign.center,
              ),
              if (!invalidLink) ...[
                const SizedBox(height: 12),
                OutlinedButton(
                  onPressed: () => ref.invalidate(publicReportProvider(token)),
                  child: const Text('Opnieuw proberen'),
                ),
              ],
            ],
          ),
        ),
      );
    }
    return const Center(child: CircularProgressIndicator());
  }
}

class _Report extends StatelessWidget {
  const _Report({required this.token, required this.report});

  final String token;
  final PublicReport report;

  @override
  Widget build(BuildContext context) {
    final vehicle = report.vehicle;
    final summary = report.summary;
    final theme = Theme.of(context);
    final verified = summary.recordsByVerification['GARAGE_VERIFIED'] ?? 0;
    final documented = summary.recordsByVerification['DOCUMENTED'] ?? 0;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('${vehicle.make} ${vehicle.model}', style: theme.textTheme.headlineSmall),
        const SizedBox(height: 4),
        Text(
          [
            if (vehicle.licensePlate != null) vehicle.licensePlate!,
            if (vehicle.modelYear != null) 'bouwjaar ${vehicle.modelYear}',
            if (vehicle.firstRegistrationDate != null)
              'eerste toelating ${formatDate(vehicle.firstRegistrationDate!)}',
            '${vehicle.registeredOwnerCount} eigenaar(s) in RepairTrack',
          ].join(' · '),
        ),
        if (vehicle.ownershipUnderReview) ...[
          const SizedBox(height: 12),
          const InfoBanner(
            warning: true,
            icon: Icons.gavel,
            message: 'Het eigendom van dit voertuig wordt op dit moment door RepairTrack beoordeeld.',
          ),
        ],
        const SizedBox(height: 16),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            _Stat(label: 'Registraties', value: '${summary.totalRecords}'),
            _Stat(label: 'Door garage', value: '$verified'),
            _Stat(label: 'Met document', value: '$documented'),
            if (summary.lastRecordedMileage != null)
              _Stat(label: 'Laatste km-stand', value: formatKm(summary.lastRecordedMileage!)),
            if (summary.voidedRecords > 0) _Stat(label: 'Ongeldig verklaard', value: '${summary.voidedRecords}'),
          ],
        ),
        const SizedBox(height: 16),
        for (final inconsistency in report.mileage.inconsistencies) ...[
          InfoBanner(
            warning: true,
            icon: Icons.speed,
            message: 'Kilometerstand ${formatKm(inconsistency.later.mileage)} op ${formatDate(inconsistency.later.date)} '
                'is lager dan ${formatKm(inconsistency.earlier.mileage)} op ${formatDate(inconsistency.earlier.date)}. '
                'Dit is een inconsistentie in de gegevens, geen vaststelling van fraude.',
          ),
          const SizedBox(height: 8),
        ],
        const SizedBox(height: 8),
        Text('Historie', style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        if (report.history.isEmpty) const Text('Er zijn nog geen registraties.'),
        for (final entry in report.history) _EntryCard(token: token, entry: entry),
        const SizedBox(height: 24),
        Text(
          'Opgesteld op ${formatDateTime(report.generatedAt)}. Deze link is geldig tot '
          '${formatDateTime(report.linkValidUntil)}. Ongeldig verklaarde registraties en correcties blijven zichtbaar: '
          'in RepairTrack wordt niets stilletjes gewijzigd of verwijderd.',
          style: theme.textTheme.bodySmall,
        ),
      ],
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(value, style: Theme.of(context).textTheme.titleMedium),
          Text(label, style: Theme.of(context).textTheme.bodySmall),
        ],
      ),
    );
  }
}

class _EntryCard extends ConsumerWidget {
  const _EntryCard({required this.token, required this.entry});

  final String token;
  final PublicEntry entry;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final titleStyle = entry.voided
        ? theme.textTheme.titleMedium?.copyWith(decoration: TextDecoration.lineThrough, color: theme.disabledColor)
        : theme.textTheme.titleMedium;
    final garage = entry.garage;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Text(entry.title, style: titleStyle)),
                if (entry.voided)
                  Text('Ongeldig', style: TextStyle(color: theme.colorScheme.error))
                else
                  VerificationBadge(status: entry.verificationStatus),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              '${formatDate(entry.eventDate)} · ${formatKm(entry.mileage)} · ${eventTypeLabel(entry.eventType)}',
              style: theme.textTheme.bodySmall,
            ),
            Text(
              garage == null
                  ? sourceTypeLabel(entry.sourceType)
                  : '${garage.name}${garage.city == null ? '' : ', ${garage.city}'}'
                      '${garage.verificationStatus == GarageVerificationStatus.verified ? ' (geverifieerd)' : ''}',
              style: theme.textTheme.bodySmall,
            ),
            if (entry.enteredDuringRevokedOwnership) ...[
              const SizedBox(height: 4),
              Text('Ingevoerd door een eigenaar van wie het eigendom na een geschil is ingetrokken', style: TextStyle(color: theme.colorScheme.error)),
            ],
            if (entry.voided && entry.voidReason != null) ...[
              const SizedBox(height: 4),
              Text('Ongeldig verklaard: ${entry.voidReason}', style: TextStyle(color: theme.colorScheme.error)),
            ],
            if (entry.description != null && entry.description!.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(entry.description!),
            ],
            if (entry.parts.isNotEmpty) ...[
              const SizedBox(height: 8),
              for (final part in entry.parts)
                Text(
                  '${part.quantity} × ${part.description}'
                  '${[part.brand, part.partNumber].whereType<String>().isEmpty ? '' : ' (${[part.brand, part.partNumber].whereType<String>().join(', ')})'}',
                  style: theme.textTheme.bodySmall,
                ),
            ],
            for (final correction in entry.corrections) ...[
              const SizedBox(height: 8),
              Text(
                'Gecorrigeerd: ${correctionFieldLabel(correction.field)} '
                '${correctionValueLabel(correction.field, correction.originalValue)} → '
                '${correctionValueLabel(correction.field, correction.correctedValue)} '
                '(${correction.reason}; door ${correction.correctedBy == 'OWNER' ? 'eigenaar' : correction.correctedBy}, '
                '${formatDate(correction.correctedAt.toLocal())})',
                style: theme.textTheme.bodySmall,
              ),
            ],
            for (final document in entry.documents)
              ListTile(
                dense: true,
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.description_outlined),
                title: Text(documentTypeLabel(document.documentType)),
                subtitle: Text(
                  document.downloadable
                      ? '${formatFileSize(document.fileSize)} · vingerafdruk ${document.reference!.substring(0, 12)}…'
                      : 'Niet gedeeld door de eigenaar',
                ),
                trailing: document.downloadable ? const Icon(Icons.open_in_new) : null,
                onTap: document.downloadable ? () => _open(context, ref, document) : null,
              ),
          ],
        ),
      ),
    );
  }

  Future<void> _open(BuildContext context, WidgetRef ref, PublicDocument document) async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      final link = await ref.read(publicReportApiProvider).documentLink(token, document.reference!);
      if (!await launchUrl(Uri.parse(link.downloadUrl), mode: LaunchMode.externalApplication)) {
        messenger.showSnackBar(const SnackBar(content: Text('Het document kon niet worden geopend.')));
      }
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(userMessage(e))));
    }
  }
}
