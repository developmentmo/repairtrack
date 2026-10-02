import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format/formatters.dart';
import '../../../core/widgets/async_value_view.dart';
import '../../../core/widgets/form_widgets.dart';
import '../application/repair_providers.dart';
import '../domain/repair.dart';
import 'repair_labels.dart';

class RepairDetailScreen extends ConsumerWidget {
  const RepairDetailScreen({super.key, required this.repairId});

  final String repairId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final repair = ref.watch(repairProvider(repairId));
    return Scaffold(
      appBar: AppBar(title: const Text('Registratie')),
      body: AsyncValueView(
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
            Text(repair.title, style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            Align(alignment: Alignment.centerLeft, child: VerificationBadge(status: repair.verificationStatus)),
            const SizedBox(height: 16),
            DetailRow(label: 'Soort', value: eventTypeLabel(repair.eventType)),
            DetailRow(label: 'Datum', value: formatDate(repair.eventDate)),
            DetailRow(label: 'Kilometerstand', value: formatKm(repair.mileage)),
            DetailRow(label: 'Bron', value: sourceTypeLabel(repair.sourceType)),
            if (repair.garage != null)
              DetailRow(
                label: 'Garage',
                value: [repair.garage!.name, repair.garage!.city].whereType<String>().join(', '),
              ),
            DetailRow(label: 'Vastgelegd op', value: formatDateTime(repair.createdAt)),
            if (repair.description != null && repair.description!.isNotEmpty) ...[
              const SizedBox(height: 16),
              Text('Omschrijving', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 4),
              Text(repair.description!),
            ],
            if (repair.parts.isNotEmpty) ...[
              const SizedBox(height: 24),
              Text('Onderdelen', style: Theme.of(context).textTheme.titleSmall),
              for (final part in repair.parts)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text('${part.quantity} × ${part.description}'),
                  subtitle: Text([part.brand, part.partNumber].whereType<String>().join(' · ')),
                ),
            ],
            if (repair.corrections.isNotEmpty) ...[
              const SizedBox(height: 24),
              Text('Correcties', style: Theme.of(context).textTheme.titleSmall),
              for (final correction in repair.corrections) _CorrectionTile(correction: correction),
            ],
          ],
        ),
      ),
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
