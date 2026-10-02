import 'package:flutter/material.dart';

import '../../../core/format/formatters.dart';
import '../domain/repair.dart';

String eventTypeLabel(RepairEventType type) => switch (type) {
      RepairEventType.maintenance => 'Onderhoud',
      RepairEventType.repair => 'Reparatie',
      RepairEventType.inspection => 'Inspectie',
      RepairEventType.tyreChange => 'Banden',
      RepairEventType.damageRepair => 'Schadeherstel',
      RepairEventType.apk => 'APK',
      RepairEventType.recall => 'Terugroepactie',
      RepairEventType.other => 'Overig',
      RepairEventType.unknown => 'Onbekend',
    };

String sourceTypeLabel(SourceType type) => switch (type) {
      SourceType.owner => 'Eigenaar',
      SourceType.ownerDocument => 'Eigenaar met document',
      SourceType.garage => 'Garage',
      SourceType.verifiedGarage => 'Geverifieerde garage',
      SourceType.manufacturer => 'Fabrikant',
      SourceType.rdw => 'RDW',
      SourceType.unknown => 'Onbekend',
    };

String verificationLabel(VerificationStatus status) => switch (status) {
      VerificationStatus.unverified => 'Niet geverifieerd',
      VerificationStatus.documented => 'Met document',
      VerificationStatus.garageVerified => 'Door garage',
      VerificationStatus.officialSource => 'Officiële bron',
      VerificationStatus.unknown => 'Onbekend',
    };

String correctionFieldLabel(String field) => switch (field) {
      'EVENT_TYPE' => 'Soort',
      'EVENT_DATE' => 'Datum',
      'MILEAGE' => 'Kilometerstand',
      'TITLE' => 'Titel',
      'DESCRIPTION' => 'Omschrijving',
      _ => field,
    };

/// Shows a corrected value in readable form (dates, kilometres and event types are sent as raw values).
String correctionValueLabel(String field, String? value) {
  if (value == null || value.isEmpty) {
    return '(leeg)';
  }
  switch (field) {
    case 'EVENT_DATE':
      return formatDate(parseWireDate(value));
    case 'MILEAGE':
      final km = int.tryParse(value);
      return km == null ? value : formatKm(km);
    case 'EVENT_TYPE':
      final type = RepairEventType.values.where((t) => t.wireName == value).firstOrNull;
      return type == null ? value : eventTypeLabel(type);
    default:
      return value;
  }
}

/// "Kilometerstand 120.000 km op 01-03-2026 is lager dan 125.000 km op 10-01-2026."
String mileageWarningText(MileageWarning warning) =>
    'Kilometerstand ${formatKm(warning.later.mileage)} op ${formatDate(warning.later.date)} is lager dan '
    '${formatKm(warning.earlier.mileage)} op ${formatDate(warning.earlier.date)}.';

/// Who stands behind a record, at a glance.
class VerificationBadge extends StatelessWidget {
  const VerificationBadge({super.key, required this.status});

  final VerificationStatus status;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final (icon, background, foreground) = switch (status) {
      VerificationStatus.garageVerified || VerificationStatus.officialSource => (
          Icons.verified,
          scheme.primaryContainer,
          scheme.onPrimaryContainer,
        ),
      VerificationStatus.documented => (Icons.description_outlined, scheme.tertiaryContainer, scheme.onTertiaryContainer),
      _ => (Icons.person_outline, scheme.surfaceContainerHighest, scheme.onSurfaceVariant),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(16)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: foreground),
          const SizedBox(width: 4),
          Text(verificationLabel(status), style: TextStyle(fontSize: 12, color: foreground)),
        ],
      ),
    );
  }
}
