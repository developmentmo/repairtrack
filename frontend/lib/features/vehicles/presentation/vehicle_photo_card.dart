import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../../core/network/error_messages.dart';
import '../../../core/widgets/brand_widgets.dart';
import '../application/vehicle_providers.dart';
import '../data/vehicle_api.dart';
import '../domain/vehicle_photo.dart';

/// The owner's photo of their vehicle (or a placeholder), with "add" or "replace". Only shown to the current owner:
/// the server refuses everyone else.
class VehiclePhotoCard extends ConsumerStatefulWidget {
  const VehiclePhotoCard({super.key, required this.vehicleId});

  final String vehicleId;

  @override
  ConsumerState<VehiclePhotoCard> createState() => _VehiclePhotoCardState();
}

class _VehiclePhotoCardState extends ConsumerState<VehiclePhotoCard> {
  bool _uploading = false;

  /// Upload progress from 0 to 1; null while unknown.
  double? _progress;

  String get vehicleId => widget.vehicleId;

  void _show(String message) {
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
    }
  }

  Future<ImageSource?> _chooseSource() => showModalBottomSheet<ImageSource>(
        context: context,
        builder: (context) => SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: const Icon(Icons.photo_library_outlined),
                title: const Text('Kies uit galerij'),
                onTap: () => Navigator.pop(context, ImageSource.gallery),
              ),
              ListTile(
                leading: const Icon(Icons.photo_camera_outlined),
                title: const Text('Maak een foto'),
                onTap: () => Navigator.pop(context, ImageSource.camera),
              ),
            ],
          ),
        ),
      );

  Future<void> _pickAndUpload() async {
    final picker = ref.read(imagePickerProvider);
    final source = picker.supportsImageSource(ImageSource.camera) ? await _chooseSource() : ImageSource.gallery;
    if (source == null) {
      return; // cancelled
    }
    final XFile? file;
    try {
      // Re-encoding (size and quality) keeps uploads small and drops metadata such as the location.
      file = await picker.pickImage(
        source: source,
        maxWidth: 2560,
        maxHeight: 2560,
        imageQuality: 85,
        requestFullMetadata: false,
      );
    } on PlatformException {
      _show('De camera of fotobibliotheek kan niet worden geopend. Controleer de toegang in je instellingen.');
      return;
    }
    if (file == null) {
      return; // cancelled
    }
    final bytes = await file.readAsBytes();
    if (!mounted) {
      return;
    }
    if (bytes.length > maxVehiclePhotoBytes) {
      _show('De foto is te groot (maximaal 20 MB).');
      return;
    }
    setState(() {
      _uploading = true;
      _progress = null;
    });
    try {
      await ref.read(vehicleApiProvider).uploadPhoto(
            vehicleId,
            fileName: file.name.isEmpty ? 'foto' : file.name,
            bytes: bytes,
            onSendProgress: (sent, total) {
              if (mounted && total > 0) {
                setState(() => _progress = sent / total);
              }
            },
          );
      ref.invalidate(vehiclePhotoProvider(vehicleId));
      _show('Foto opgeslagen.');
    } catch (e) {
      _show(vehiclePhotoMessage(e));
    } finally {
      if (mounted) {
        setState(() => _uploading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final photo = ref.watch(vehiclePhotoProvider(vehicleId));
    final current = photo.value;
    final loadFailed = photo.hasError && !photo.hasValue;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 560),
                child: AspectRatio(
                  aspectRatio: 16 / 9,
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(10),
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        if (current != null)
                          _PhotoImage(photo: current, onReload: () => ref.invalidate(vehiclePhotoProvider(vehicleId)))
                        else
                          const VehiclePicture(key: Key('vehicle-photo-placeholder'), height: 160),
                        if (_uploading || (photo.isLoading && !photo.hasValue))
                          ColoredBox(
                            color: Colors.black26,
                            child: Center(child: CircularProgressIndicator(value: _uploading ? _progress : null)),
                          ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
            if (loadFailed) ...[
              const SizedBox(height: 8),
              Text(vehiclePhotoMessage(photo.error!), style: Theme.of(context).textTheme.bodySmall),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(
                  onPressed: () => ref.invalidate(vehiclePhotoProvider(vehicleId)),
                  child: const Text('Opnieuw proberen'),
                ),
              ),
            ],
            const SizedBox(height: 12),
            Align(
              alignment: Alignment.centerLeft,
              child: current == null
                  ? FilledButton.icon(
                      onPressed: _uploading || !photo.hasValue ? null : _pickAndUpload,
                      icon: const Icon(Icons.add_a_photo_outlined),
                      label: const Text('Foto toevoegen'),
                    )
                  : OutlinedButton.icon(
                      onPressed: _uploading ? null : _pickAndUpload,
                      icon: const Icon(Icons.photo_camera_outlined),
                      label: const Text('Foto vervangen'),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

/// The photo from its short-lived URL. When loading fails (for example because the URL expired), the placeholder
/// is shown with a way to fetch a new URL.
class _PhotoImage extends StatelessWidget {
  const _PhotoImage({required this.photo, required this.onReload});

  final VehiclePhoto photo;
  final VoidCallback onReload;

  @override
  Widget build(BuildContext context) {
    return Image.network(
      photo.downloadUrl,
      key: ValueKey(photo.downloadUrl),
      fit: BoxFit.cover,
      semanticLabel: 'Foto van je voertuig',
      errorBuilder: (context, error, stackTrace) => Stack(
        fit: StackFit.expand,
        children: [
          const VehiclePicture(height: 160),
          Align(
            alignment: Alignment.bottomCenter,
            child: TextButton.icon(
              onPressed: onReload,
              icon: const Icon(Icons.refresh),
              label: const Text('Foto opnieuw laden'),
            ),
          ),
        ],
      ),
    );
  }
}
