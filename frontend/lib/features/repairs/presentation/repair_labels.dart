import 'package:flutter/material.dart';

import '../../../core/format/formatters.dart';
import '../../../core/widgets/brand_widgets.dart';
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
      VerificationStatus.unverified => 'Onbevestigd',
      VerificationStatus.documented => 'Met document',
      VerificationStatus.garageVerified => 'Garage bevestigd',
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

/// Icon and colour for an event type, used in the history timeline and recent-repair lists.
(IconData, Tone) eventTypeVisual(RepairEventType type) => switch (type) {
      RepairEventType.maintenance => (Icons.build_outlined, Tone.info),
      RepairEventType.repair => (Icons.car_repair_outlined, Tone.warning),
      RepairEventType.inspection => (Icons.search, Tone.info),
      RepairEventType.tyreChange => (Icons.tire_repair_outlined, Tone.success),
      RepairEventType.damageRepair => (Icons.car_crash_outlined, Tone.danger),
      RepairEventType.apk => (Icons.fact_check_outlined, Tone.purple),
      RepairEventType.recall => (Icons.campaign_outlined, Tone.warning),
      RepairEventType.other || RepairEventType.unknown => (Icons.more_horiz, Tone.neutral),
    };

/// Round, coloured icon for an event type.
class EventTypeIcon extends StatelessWidget {
  const EventTypeIcon({super.key, required this.type, this.size = 40});

  final RepairEventType type;
  final double size;

  @override
  Widget build(BuildContext context) {
    final (icon, tone) = eventTypeVisual(type);
    return IconBadge(icon: icon, tone: tone, size: size);
  }
}

/// Who stands behind a record, at a glance.
class VerificationBadge extends StatelessWidget {
  const VerificationBadge({super.key, required this.status});

  final VerificationStatus status;

  @override
  Widget build(BuildContext context) {
    final (icon, tone) = switch (status) {
      VerificationStatus.garageVerified => (Icons.verified_outlined, Tone.success),
      VerificationStatus.officialSource => (Icons.account_balance_outlined, Tone.info),
      VerificationStatus.documented => (Icons.description_outlined, Tone.purple),
      VerificationStatus.unverified => (Icons.person_outline, Tone.warning),
      VerificationStatus.unknown => (Icons.help_outline, Tone.neutral),
    };
    return StatusPill(label: verificationLabel(status), tone: tone, icon: icon);
  }
}
