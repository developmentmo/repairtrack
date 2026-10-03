import 'package:json_annotation/json_annotation.dart';

import '../../../core/json/date_only_converter.dart';
import '../../documents/domain/document.dart';
import '../../garages/domain/garage.dart';
import '../../repairs/domain/repair.dart';

part 'public_report.g.dart';

/// `GET /public/vehicles/{token}`: the shared, privacy-safe history. It has no IDs, no VIN, no owner
/// identity and no file names by design.
@JsonSerializable(createToJson: false)
class PublicReport {
  const PublicReport({
    required this.vehicle,
    required this.summary,
    required this.history,
    required this.mileage,
    required this.documentsDownloadable,
    required this.generatedAt,
    required this.linkValidUntil,
  });

  factory PublicReport.fromJson(Map<String, dynamic> json) => _$PublicReportFromJson(json);

  final PublicVehicle vehicle;
  final PublicSummary summary;
  final List<PublicEntry> history;
  final PublicMileage mileage;
  final bool documentsDownloadable;
  final DateTime generatedAt;
  final DateTime linkValidUntil;
}

@JsonSerializable(createToJson: false)
class PublicVehicle {
  const PublicVehicle({
    required this.make,
    required this.model,
    required this.registeredOwnerCount,
    this.modelYear,
    this.firstRegistrationDate,
    this.licensePlate,
  });

  factory PublicVehicle.fromJson(Map<String, dynamic> json) => _$PublicVehicleFromJson(json);

  final String make;
  final String model;
  final int? modelYear;
  @NullableDateOnlyConverter()
  final DateTime? firstRegistrationDate;
  final String? licensePlate;
  final int registeredOwnerCount;
}

@JsonSerializable(createToJson: false)
class PublicSummary {
  const PublicSummary({
    required this.totalRecords,
    required this.voidedRecords,
    required this.recordsByVerification,
    required this.mileageInconsistencies,
    required this.documentCount,
    this.firstEventDate,
    this.lastEventDate,
    this.lastRecordedMileage,
  });

  factory PublicSummary.fromJson(Map<String, dynamic> json) => _$PublicSummaryFromJson(json);

  final int totalRecords;
  final int voidedRecords;

  /// Keys are verification statuses as sent by the backend (`GARAGE_VERIFIED`, ...).
  final Map<String, int> recordsByVerification;
  @NullableDateOnlyConverter()
  final DateTime? firstEventDate;
  @NullableDateOnlyConverter()
  final DateTime? lastEventDate;
  final int? lastRecordedMileage;
  final int mileageInconsistencies;
  final int documentCount;
}

@JsonSerializable(createToJson: false)
class PublicEntry {
  const PublicEntry({
    required this.eventType,
    required this.eventDate,
    required this.mileage,
    required this.title,
    required this.sourceType,
    required this.verificationStatus,
    required this.voided,
    this.description,
    this.voidReason,
    this.garage,
    this.parts = const [],
    this.corrections = const [],
    this.documents = const [],
  });

  factory PublicEntry.fromJson(Map<String, dynamic> json) => _$PublicEntryFromJson(json);

  @JsonKey(unknownEnumValue: RepairEventType.unknown)
  final RepairEventType eventType;
  @DateOnlyConverter()
  final DateTime eventDate;
  final int mileage;
  final String title;
  final String? description;
  @JsonKey(unknownEnumValue: SourceType.unknown)
  final SourceType sourceType;
  @JsonKey(unknownEnumValue: VerificationStatus.unknown)
  final VerificationStatus verificationStatus;
  final bool voided;
  final String? voidReason;
  final PublicGarage? garage;
  final List<PublicPart> parts;
  final List<PublicCorrection> corrections;
  final List<PublicDocument> documents;
}

@JsonSerializable(createToJson: false)
class PublicGarage {
  const PublicGarage({required this.name, required this.verificationStatus, this.city});

  factory PublicGarage.fromJson(Map<String, dynamic> json) => _$PublicGarageFromJson(json);

  final String name;
  final String? city;
  @JsonKey(unknownEnumValue: GarageVerificationStatus.unknown)
  final GarageVerificationStatus verificationStatus;
}

@JsonSerializable(createToJson: false)
class PublicPart {
  const PublicPart({required this.description, required this.quantity, this.partNumber, this.brand});

  factory PublicPart.fromJson(Map<String, dynamic> json) => _$PublicPartFromJson(json);

  final String? partNumber;
  final String? brand;
  final String description;
  final int quantity;
}

@JsonSerializable(createToJson: false)
class PublicCorrection {
  const PublicCorrection({
    required this.field,
    required this.reason,
    required this.correctedBy,
    required this.correctedAt,
    this.originalValue,
    this.correctedValue,
  });

  factory PublicCorrection.fromJson(Map<String, dynamic> json) => _$PublicCorrectionFromJson(json);

  final String field;
  final String? originalValue;
  final String? correctedValue;
  final String reason;

  /// "OWNER" or the garage's name.
  final String correctedBy;
  final DateTime correctedAt;
}

@JsonSerializable(createToJson: false)
class PublicDocument {
  const PublicDocument({
    required this.documentType,
    required this.mimeType,
    required this.fileSize,
    required this.uploadedAt,
    required this.downloadable,
    this.reference,
  });

  factory PublicDocument.fromJson(Map<String, dynamic> json) => _$PublicDocumentFromJson(json);

  @JsonKey(unknownEnumValue: DocumentType.unknown)
  final DocumentType documentType;
  final String mimeType;
  final int fileSize;
  final DateTime uploadedAt;
  final bool downloadable;

  /// The document's SHA-256: download reference and integrity fingerprint. Only set when downloadable.
  final String? reference;
}

@JsonSerializable(createToJson: false)
class PublicMileage {
  const PublicMileage({required this.readings, required this.inconsistencies});

  factory PublicMileage.fromJson(Map<String, dynamic> json) => _$PublicMileageFromJson(json);

  final List<MileageReading> readings;
  final List<PublicInconsistency> inconsistencies;
}

@JsonSerializable(createToJson: false)
class PublicInconsistency {
  const PublicInconsistency({required this.message, required this.earlier, required this.later});

  factory PublicInconsistency.fromJson(Map<String, dynamic> json) => _$PublicInconsistencyFromJson(json);

  final String message;
  final MileageReading earlier;
  final MileageReading later;
}

@JsonSerializable(createToJson: false)
class PublicDocumentLink {
  const PublicDocumentLink({required this.downloadUrl, required this.expiresAt});

  factory PublicDocumentLink.fromJson(Map<String, dynamic> json) => _$PublicDocumentLinkFromJson(json);

  final String downloadUrl;
  final DateTime expiresAt;
}
