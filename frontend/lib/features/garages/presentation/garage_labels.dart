import 'package:flutter/material.dart';

import '../../../core/l10n/l10n.dart';
import '../../../core/widgets/brand_widgets.dart';
import '../domain/garage.dart';

String garageStatusLabel(AppLocalizations l, GarageVerificationStatus status) => switch (status) {
      GarageVerificationStatus.verified => l.garageStatusVerified,
      GarageVerificationStatus.pending => l.garageStatusPending,
      GarageVerificationStatus.unverified => l.garageStatusUnverified,
      GarageVerificationStatus.suspended => l.garageStatusSuspended,
      GarageVerificationStatus.unknown => l.commonUnknown,
    };

String garageRoleLabel(AppLocalizations l, GarageRole role) => switch (role) {
      GarageRole.garageAdmin => l.garageRoleAdmin,
      GarageRole.mechanic => l.garageRoleMechanic,
      GarageRole.unknown => l.garageRoleMember,
    };

class GarageStatusChip extends StatelessWidget {
  const GarageStatusChip({super.key, required this.status});

  final GarageVerificationStatus status;

  @override
  Widget build(BuildContext context) {
    final (icon, tone) = switch (status) {
      GarageVerificationStatus.verified => (Icons.verified_outlined, Tone.success),
      GarageVerificationStatus.suspended => (Icons.block, Tone.danger),
      GarageVerificationStatus.pending => (Icons.hourglass_empty, Tone.warning),
      _ => (Icons.hourglass_empty, Tone.neutral),
    };
    return StatusPill(label: garageStatusLabel(context.l10n, status), tone: tone, icon: icon);
  }
}
