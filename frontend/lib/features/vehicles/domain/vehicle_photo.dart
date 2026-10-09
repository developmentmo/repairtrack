import 'package:json_annotation/json_annotation.dart';

part 'vehicle_photo.g.dart';

/// `VehiclePhotoResponse`: the current owner's photo of their vehicle. The file lives in private storage;
/// [downloadUrl] is a presigned URL that is valid for a few minutes. Never part of the public report.
@JsonSerializable(createToJson: false)
class VehiclePhoto {
  const VehiclePhoto({
    required this.id,
    required this.vehicleId,
    required this.mimeType,
    required this.fileSize,
    required this.sha256,
    required this.uploadedAt,
    required this.downloadUrl,
    required this.downloadUrlExpiresAt,
  });

  factory VehiclePhoto.fromJson(Map<String, dynamic> json) => _$VehiclePhotoFromJson(json);

  final String id;
  final String vehicleId;
  final String mimeType;
  final int fileSize;

  /// Fingerprint computed by the server over the stored bytes.
  final String sha256;
  final DateTime uploadedAt;
  final String downloadUrl;
  final DateTime downloadUrlExpiresAt;
}

/// Backend limit (also enforced by the server).
const maxVehiclePhotoBytes = 20 * 1024 * 1024;
