import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/routing/routes.dart';
import '../domain/repair.dart';
import 'repair_labels.dart';

/// One repair as a row: coloured type icon, title, a detail line, kilometres and verification.
/// Used for "Recente reparaties" on the dashboard and the vehicle screen.
class RepairRow extends StatelessWidget {
  const RepairRow({super.key, required this.repair, this.vehicleName});

  final Repair repair;

  /// Shown instead of the garage when repairs of several vehicles are listed together.
  final String? vehicleName;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final detail = [
      vehicleName ?? repair.garage?.name ?? sourceTypeLabel(repair.sourceType),
      formatDate(repair.eventDate),
    ].join(' · ');
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: () => context.go(Routes.repair(repair.id)),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 4),
        child: Row(
          children: [
            EventTypeIcon(type: repair.eventType),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(repair.title, style: theme.textTheme.titleSmall, overflow: TextOverflow.ellipsis),
                  const SizedBox(height: 2),
                  Text(detail, style: theme.textTheme.bodySmall, overflow: TextOverflow.ellipsis),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(formatKm(repair.mileage), style: theme.textTheme.bodySmall),
                const SizedBox(height: 4),
                VerificationBadge(status: repair.verificationStatus),
              ],
            ),
            const Icon(Icons.chevron_right, size: 20),
          ],
        ),
      ),
    );
  }
}

/// One entry in the history timeline: an icon on a vertical line, with the record next to it.
/// Voided records stay visible, struck through.
class RepairTimelineTile extends StatelessWidget {
  const RepairTimelineTile({super.key, required this.repair, this.isFirst = false, this.isLast = false});

  final Repair repair;
  final bool isFirst;
  final bool isLast;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final voided = repair.isVoided;
    // On phones the status goes under the text, so the title keeps the full width.
    final compact = MediaQuery.sizeOf(context).width < 600;
    final status = voided
        ? Text('Ongeldig', style: TextStyle(color: theme.colorScheme.error))
        : VerificationBadge(status: repair.verificationStatus);
    final lineColor = theme.colorScheme.outlineVariant;
    final titleStyle = voided
        ? theme.textTheme.titleSmall?.copyWith(decoration: TextDecoration.lineThrough, color: theme.disabledColor)
        : theme.textTheme.titleSmall;
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            width: 40,
            child: Column(
              children: [
                Container(width: 2, height: 14, color: isFirst ? Colors.transparent : lineColor),
                EventTypeIcon(type: repair.eventType),
                Expanded(child: Container(width: 2, color: isLast ? Colors.transparent : lineColor)),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: InkWell(
              borderRadius: BorderRadius.circular(12),
              onTap: () => context.go(Routes.repair(repair.id)),
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 4),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(repair.title, style: titleStyle),
                          const SizedBox(height: 2),
                          Text(
                            repair.garage?.name ?? sourceTypeLabel(repair.sourceType),
                            style: theme.textTheme.bodySmall,
                          ),
                          Text(
                            '${formatDate(repair.eventDate)} · ${formatKm(repair.mileage)} · '
                            '${eventTypeLabel(repair.eventType)}',
                            style: theme.textTheme.bodySmall,
                          ),
                          if (compact) ...[
                            const SizedBox(height: 6),
                            status,
                          ],
                          if (repair.enteredDuringRevokedOwnership) ...[
                            const SizedBox(height: 2),
                            Text(
                              'Ingevoerd door een eigenaar van wie het eigendom na een geschil is ingetrokken',
                              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.error),
                            ),
                          ],
                        ],
                      ),
                    ),
                    if (!compact) ...[
                      const SizedBox(width: 8),
                      status,
                    ],
                    const Icon(Icons.chevron_right, size: 20),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
