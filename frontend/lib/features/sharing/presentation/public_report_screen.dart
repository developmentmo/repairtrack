import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/format/formatters.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/widgets/brand_widgets.dart';
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
      appBar: AppBar(title: const BrandLogo(size: 26), automaticallyImplyLeading: false),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 760),
          child: _body(context, ref, report),
        ),
      ),
    );
  }

  Widget _body(BuildContext context, WidgetRef ref, AsyncValue<PublicReport> report) {
    final l = context.l10n;
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
                    ? l.publicReportInvalidLink
                    : userMessage(l, error!),
                textAlign: TextAlign.center,
              ),
              if (!invalidLink) ...[
                const SizedBox(height: 12),
                OutlinedButton(
                  onPressed: () => ref.invalidate(publicReportProvider(token)),
                  child: Text(l.commonRetry),
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
    final l = context.l10n;
    final vehicle = report.vehicle;
    final summary = report.summary;
    final theme = Theme.of(context);
    final verified = summary.recordsByVerification['GARAGE_VERIFIED'] ?? 0;
    final documented = summary.recordsByVerification['DOCUMENTED'] ?? 0;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(l.publicReportTitle, style: theme.textTheme.headlineSmall),
        const SizedBox(height: 4),
        Text(l.publicReportSharedByOwner, style: theme.textTheme.bodySmall),
        const SizedBox(height: 16),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                const VehiclePicture(width: 110, height: 76),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('${vehicle.make} ${vehicle.model}', style: theme.textTheme.titleLarge),
                      Text(
                        [
                          if (vehicle.modelYear != null) l.publicReportModelYear('${vehicle.modelYear}'),
                          if (vehicle.firstRegistrationDate != null)
                            l.publicReportFirstRegistration(formatDate(vehicle.firstRegistrationDate!)),
                        ].join(' · '),
                        style: theme.textTheme.bodySmall,
                      ),
                      if (vehicle.licensePlate != null) ...[
                        const SizedBox(height: 8),
                        LicensePlate(plate: vehicle.licensePlate!),
                      ],
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
        if (vehicle.ownershipUnderReview) ...[
          const SizedBox(height: 12),
          InfoBanner(
            warning: true,
            icon: Icons.gavel,
            message: l.publicReportUnderReview,
          ),
        ],
        const SizedBox(height: 4),
        SectionCard(
          title: l.publicReportSummary,
          child: Wrap(
            spacing: 16,
            runSpacing: 12,
            children: [
              _Stat(icon: Icons.build_outlined, label: l.publicReportStatTotal, value: '${summary.totalRecords}'),
              _Stat(icon: Icons.verified_outlined, label: l.publicReportStatVerified, value: '$verified'),
              _Stat(icon: Icons.description_outlined, label: l.publicReportStatDocumented, value: '$documented'),
              if (summary.lastRecordedMileage != null)
                _Stat(icon: Icons.speed, label: l.publicReportStatLastMileage, value: formatKm(summary.lastRecordedMileage!)),
              _Stat(
                icon: Icons.people_outline,
                label: l.publicReportStatOwners,
                value: '${vehicle.registeredOwnerCount}',
              ),
              if (summary.voidedRecords > 0)
                _Stat(icon: Icons.block, label: l.publicReportStatVoided, value: '${summary.voidedRecords}'),
            ],
          ),
        ),
        const SizedBox(height: 16),
        for (final inconsistency in report.mileage.inconsistencies) ...[
          InfoBanner(
            warning: true,
            icon: Icons.speed,
            message: l.publicReportMileageInconsistency(
              formatKm(inconsistency.later.mileage),
              formatDate(inconsistency.later.date),
              formatKm(inconsistency.earlier.mileage),
              formatDate(inconsistency.earlier.date),
            ),
          ),
          const SizedBox(height: 8),
        ],
        const SizedBox(height: 8),
        Text(l.publicReportRecords, style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        if (report.history.isEmpty) Text(l.publicReportNoRecords),
        for (final entry in report.history) _EntryCard(token: token, entry: entry),
        const SizedBox(height: 16),
        InfoBanner(message: l.publicReportDisclaimer),
        const SizedBox(height: 12),
        Text(
          l.publicReportFooter(formatDateTime(report.generatedAt), formatDateTime(report.linkValidUntil)),
          style: theme.textTheme.bodySmall,
        ),
      ],
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat({required this.icon, required this.label, required this.value});

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 200,
      child: Row(
        children: [
          IconBadge(icon: icon, size: 36),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: Theme.of(context).textTheme.bodySmall),
                Text(value, style: Theme.of(context).textTheme.titleMedium),
              ],
            ),
          ),
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
    final l = context.l10n;
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
                EventTypeIcon(type: entry.eventType, size: 36),
                const SizedBox(width: 12),
                Expanded(child: Text(entry.title, style: titleStyle)),
                if (entry.voided)
                  Text(l.publicReportVoid, style: TextStyle(color: theme.colorScheme.error))
                else
                  VerificationBadge(status: entry.verificationStatus),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              '${formatDate(entry.eventDate)} · ${formatKm(entry.mileage)} · ${eventTypeLabel(l, entry.eventType)}',
              style: theme.textTheme.bodySmall,
            ),
            Text(
              garage == null
                  ? sourceTypeLabel(l, entry.sourceType)
                  : _garageLine(l, garage),
              style: theme.textTheme.bodySmall,
            ),
            if (entry.enteredDuringRevokedOwnership) ...[
              const SizedBox(height: 4),
              Text(l.publicReportRevokedOwnership, style: TextStyle(color: theme.colorScheme.error)),
            ],
            if (entry.voided && entry.voidReason != null) ...[
              const SizedBox(height: 4),
              Text(l.publicReportVoidedReason(entry.voidReason!), style: TextStyle(color: theme.colorScheme.error)),
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
                l.publicReportCorrection(
                  correctionFieldLabel(l, correction.field),
                  correctionValueLabel(l, correction.field, correction.originalValue),
                  correctionValueLabel(l, correction.field, correction.correctedValue),
                  correction.reason,
                  correction.correctedBy == 'OWNER' ? l.publicReportCorrectedByOwner : correction.correctedBy,
                  formatDate(correction.correctedAt.toLocal()),
                ),
                style: theme.textTheme.bodySmall,
              ),
            ],
            for (final document in entry.documents)
              ListTile(
                dense: true,
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.description_outlined),
                title: Text(documentTypeLabel(l, document.documentType)),
                subtitle: Text(
                  document.downloadable
                      ? l.publicReportDocumentFingerprint(
                          formatFileSize(document.fileSize), document.reference!.substring(0, 12))
                      : l.publicReportDocumentNotShared,
                ),
                trailing: document.downloadable ? const Icon(Icons.open_in_new) : null,
                onTap: document.downloadable ? () => _open(context, ref, document) : null,
              ),
          ],
        ),
      ),
    );
  }

  static String _garageLine(AppLocalizations l, PublicGarage garage) {
    final name = '${garage.name}${garage.city == null ? '' : ', ${garage.city}'}';
    return garage.verificationStatus == GarageVerificationStatus.verified ? l.publicReportGarageVerified(name) : name;
  }

  Future<void> _open(BuildContext context, WidgetRef ref, PublicDocument document) async {
    final l = context.l10n;
    final messenger = ScaffoldMessenger.of(context);
    try {
      final link = await ref.read(publicReportApiProvider).documentLink(token, document.reference!);
      if (!await launchUrl(Uri.parse(link.downloadUrl), mode: LaunchMode.externalApplication)) {
        messenger.showSnackBar(SnackBar(content: Text(l.publicReportDocumentOpenFailed)));
      }
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(userMessage(l, e))));
    }
  }
}
