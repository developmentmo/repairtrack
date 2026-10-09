import 'package:json_annotation/json_annotation.dart';

part 'document.g.dart';

@JsonEnum(fieldRename: FieldRename.screamingSnake, alwaysCreate: true)
enum DocumentType {
  invoice,
  workOrder,
  inspectionReport,
  photo,
  other,
  unknown;

  static const selectable = [invoice, workOrder, inspectionReport, photo, other];

  String get wireName => _$DocumentTypeEnumMap[this]!;

  /// These types make an owner's own record "documented"; photos and other files do not.
  bool get isEvidence => this == invoice || this == workOrder || this == inspectionReport;
}

/// `DocumentResponse`. The file itself lives in private storage; [downloadUrl] is a short-lived presigned URL
/// that is only present on `GET /documents/{id}`.
@JsonSerializable(createToJson: false)
class RepairDocument {
  const RepairDocument({
    required this.id,
    required this.repairEventId,
    required this.documentType,
    required this.fileName,
    required this.mimeType,
    required this.fileSize,
    required this.sha256,
    required this.uploadedAt,
    this.repairVerificationRaised = false,
    this.downloadUrl,
    this.downloadUrlExpiresAt,
  });

  factory RepairDocument.fromJson(Map<String, dynamic> json) => _$RepairDocumentFromJson(json);

  final String id;
  final String repairEventId;
  @JsonKey(unknownEnumValue: DocumentType.unknown)
  final DocumentType documentType;
  final String fileName;
  final String mimeType;
  final int fileSize;

  /// Fingerprint computed by the server over the stored bytes.
  final String sha256;
  final DateTime uploadedAt;

  /// True in the upload response when this document raised the record to "documented".
  final bool repairVerificationRaised;
  final String? downloadUrl;
  final DateTime? downloadUrlExpiresAt;

  bool get isImage => mimeType.startsWith('image/');
}

/// Backend limit (also enforced by the server).
const maxDocumentBytes = 20 * 1024 * 1024;

const allowedDocumentExtensions = ['pdf', 'jpg', 'jpeg', 'png'];

String formatFileSize(int bytes) {
  if (bytes < 1024) {
    return '$bytes B';
  }
  if (bytes < 1024 * 1024) {
    return '${(bytes / 1024).toStringAsFixed(0)} kB';
  }
  return '${(bytes / (1024 * 1024)).toStringAsFixed(1).replaceAll('.', ',')} MB';
}
