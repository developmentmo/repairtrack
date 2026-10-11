import 'package:flutter/material.dart';

import '../../../core/format/formatters.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/widgets/brand_widgets.dart';
import '../domain/repair.dart';

String eventTypeLabel(AppLocalizations l, RepairEventType type) => switch (type) {
      RepairEventType.maintenance => l.eventTypeMaintenance,
      RepairEventType.repair => l.eventTypeRepair,
      RepairEventType.inspection => l.eventTypeInspection,
      RepairEventType.tyreChange => l.eventTypeTyreChange,
      RepairEventType.damageRepair => l.eventTypeDamageRepair,
      RepairEventType.apk => l.eventTypeApk,
      RepairEventType.recall => l.eventTypeRecall,
      RepairEventType.other => l.eventTypeOther,
      RepairEventType.unknown => l.commonUnknown,
    };

String sourceTypeLabel(AppLocalizations l, SourceType type) => switch (type) {
      SourceType.owner => l.sourceTypeOwner,
      SourceType.ownerDocument => l.sourceTypeOwnerDocument,
      SourceType.garage => l.sourceTypeGarage,
      SourceType.verifiedGarage => l.sourceTypeVerifiedGarage,
      SourceType.manufacturer => l.sourceTypeManufacturer,
      SourceType.rdw => l.sourceTypeRdw,
      SourceType.unknown => l.commonUnknown,
    };

String verificationLabel(AppLocalizations l, VerificationStatus status) => switch (status) {
      VerificationStatus.unverified => l.verificationUnverified,
      VerificationStatus.documented => l.verificationDocumented,
      VerificationStatus.garageVerified => l.verificationGarageVerified,
      VerificationStatus.officialSource => l.verificationOfficialSource,
      VerificationStatus.unknown => l.commonUnknown,
    };

String correctionFieldLabel(AppLocalizations l, String field) => switch (field) {
      'EVENT_TYPE' => l.correctionFieldEventType,
      'EVENT_DATE' => l.correctionFieldEventDate,
      'MILEAGE' => l.correctionFieldMileage,
      'TITLE' => l.correctionFieldTitle,
      'DESCRIPTION' => l.correctionFieldDescription,
      _ => field,
    };

/// Shows a corrected value in readable form (dates, kilometres and event types are sent as raw values).
String correctionValueLabel(AppLocalizations l, String field, String? value) {
  if (value == null || value.isEmpty) {
    return l.correctionValueEmpty;
  }
  switch (field) {
    case 'EVENT_DATE':
      return formatDate(parseWireDate(value));
    case 'MILEAGE':
      final km = int.tryParse(value);
      return km == null ? value : formatKm(km);
    case 'EVENT_TYPE':
      final type = RepairEventType.values.where((t) => t.wireName == value).firstOrNull;
      return type == null ? value : eventTypeLabel(l, type);
    default:
      return value;
  }
}

/// "Kilometerstand 120.000 km op 01-03-2026 is lager dan 125.000 km op 10-01-2026."
String mileageWarningText(AppLocalizations l, MileageWarning warning) => l.mileageWarning(
      formatKm(warning.later.mileage),
      formatDate(warning.later.date),
      formatKm(warning.earlier.mileage),
      formatDate(warning.earlier.date),
    );

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
    return StatusPill(label: verificationLabel(context.l10n, status), tone: tone, icon: icon);
  }
}
