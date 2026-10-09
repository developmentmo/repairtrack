import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/widgets/brand_widgets.dart';
import '../application/vehicle_providers.dart';
import '../domain/vehicle.dart';

/// The owner's photo of [vehicle] for lists such as the dashboard, or the placeholder while it loads, when there is
/// none, or when it can't be shown. Only the current owner may see the photo, so it is only fetched for them.
class VehicleThumbnail extends ConsumerWidget {
  const VehicleThumbnail({super.key, required this.vehicle, this.height = 120, this.radius = 0});

  final Vehicle vehicle;
  final double height;
  final double radius;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final placeholder = VehiclePicture(key: const Key('vehicle-thumbnail-placeholder'), height: height, radius: radius);
    if (!vehicle.ownedByMe) {
      return placeholder;
    }
    final photo = ref.watch(vehiclePhotoProvider(vehicle.id)).value;
    if (photo == null) {
      return placeholder;
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: Image.network(
        photo.downloadUrl,
        key: ValueKey(photo.downloadUrl),
        height: height,
        width: double.infinity,
        fit: BoxFit.cover,
        semanticLabel: 'Foto van ${vehicle.displayName}',
        // An expired link shows the placeholder; pulling to refresh fetches a new one.
        errorBuilder: (context, error, stackTrace) => placeholder,
      ),
    );
  }
}
