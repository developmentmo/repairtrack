import 'package:flutter/material.dart';

import '../../../core/widgets/brand_widgets.dart';
import '../domain/garage.dart';

String garageStatusLabel(GarageVerificationStatus status) => switch (status) {
      GarageVerificationStatus.verified => 'Geverifieerd',
      GarageVerificationStatus.pending => 'Wacht op verificatie',
      GarageVerificationStatus.unverified => 'Niet geverifieerd',
      GarageVerificationStatus.suspended => 'Geschorst',
      GarageVerificationStatus.unknown => 'Onbekend',
    };

String garageRoleLabel(GarageRole role) => switch (role) {
      GarageRole.garageAdmin => 'Beheerder',
      GarageRole.mechanic => 'Monteur',
      GarageRole.unknown => 'Lid',
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
    return StatusPill(label: garageStatusLabel(status), tone: tone, icon: icon);
  }
}
