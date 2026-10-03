import 'package:flutter/material.dart';

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
    final scheme = Theme.of(context).colorScheme;
    final (icon, background, foreground) = switch (status) {
      GarageVerificationStatus.verified => (Icons.verified, scheme.primaryContainer, scheme.onPrimaryContainer),
      GarageVerificationStatus.suspended => (Icons.block, scheme.errorContainer, scheme.onErrorContainer),
      _ => (Icons.hourglass_empty, scheme.surfaceContainerHighest, scheme.onSurfaceVariant),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(16)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: foreground),
          const SizedBox(width: 4),
          Text(garageStatusLabel(status), style: TextStyle(fontSize: 12, color: foreground)),
        ],
      ),
    );
  }
}
