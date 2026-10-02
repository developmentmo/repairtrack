import 'package:json_annotation/json_annotation.dart';

import '../../../core/json/date_only_converter.dart';

part 'repair.g.dart';

// Enums mirror the backend. `unknown` catches values added by a newer backend, so an older app keeps working.

@JsonEnum(fieldRename: FieldRename.screamingSnake, alwaysCreate: true)
enum RepairEventType {
  maintenance,
  repair,
  inspection,
  tyreChange,
  damageRepair,
  apk,
  recall,
  other,
  unknown;

  /// Event types a user can choose when creating a record.
  static const selectable = [maintenance, repair, inspection, tyreChange, damageRepair, apk, recall, other];

  String get wireName => _$RepairEventTypeEnumMap[this]!;
}

@JsonEnum(fieldRename: FieldRename.screamingSnake)
enum SourceType { owner, ownerDocument, garage, verifiedGarage, manufacturer, rdw, unknown }

@JsonEnum(fieldRename: FieldRename.screamingSnake)
enum VerificationStatus { unverified, documented, garageVerified, officialSource, unknown }

@JsonEnum(fieldRename: FieldRename.screamingSnake)
enum RepairStatus { active, voided, unknown }

/// `RepairResponse`: one entry in the vehicle history. Source type and verification status are always
/// decided by the backend.
@JsonSerializable(createToJson: false)
class Repair {
  const Repair({
    required this.id,
    required this.vehicleId,
    required this.eventType,
    required this.eventDate,
    required this.mileage,
    required this.title,
    required this.sourceType,
    required this.verificationStatus,
    required this.status,
    required this.createdAt,
    this.description,
    this.garage,
    this.parts = const [],
    this.corrections = const [],
    this.voidedAt,
    this.voidReason,
    this.warnings = const [],
  });

  factory Repair.fromJson(Map<String, dynamic> json) => _$RepairFromJson(json);

  final String id;
  final String vehicleId;
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
  @JsonKey(unknownEnumValue: RepairStatus.unknown)
  final RepairStatus status;

  /// Null for records made by the owner.
  final GarageInfo? garage;
  final List<Part> parts;
  final List<Correction> corrections;
  final DateTime? voidedAt;
  final String? voidReason;
  final DateTime createdAt;

  /// Only filled in the response to a create/correct request.
  final List<MileageWarning> warnings;

  bool get isVoided => status == RepairStatus.voided;
}

@JsonSerializable(createToJson: false)
class GarageInfo {
  const GarageInfo({required this.id, required this.name, this.city, this.verificationStatus});

  factory GarageInfo.fromJson(Map<String, dynamic> json) => _$GarageInfoFromJson(json);

  final String id;
  final String name;
  final String? city;

  /// `UNVERIFIED`, `PENDING`, `VERIFIED`, `SUSPENDED`.
  final String? verificationStatus;
}

@JsonSerializable(createToJson: false)
class Part {
  const Part({required this.description, required this.quantity, this.partNumber, this.brand});

  factory Part.fromJson(Map<String, dynamic> json) => _$PartFromJson(json);

  final String? partNumber;
  final String? brand;
  final String description;
  final int quantity;
}

/// One corrected field. The original value stays visible: records are corrected, never silently changed.
@JsonSerializable(createToJson: false)
class Correction {
  const Correction({
    required this.field,
    required this.reason,
    required this.correctedAt,
    this.originalValue,
    this.correctedValue,
    this.correctedByGarage,
  });

  factory Correction.fromJson(Map<String, dynamic> json) => _$CorrectionFromJson(json);

  /// `EVENT_TYPE`, `EVENT_DATE`, `MILEAGE`, `TITLE`, `DESCRIPTION`.
  final String field;
  final String? originalValue;
  final String? correctedValue;
  final String reason;

  /// Null when the owner made the correction.
  final GarageInfo? correctedByGarage;
  final DateTime correctedAt;
}

@JsonSerializable(createToJson: false)
class MileageReading {
  const MileageReading({required this.date, required this.mileage, required this.sourceType});

  factory MileageReading.fromJson(Map<String, dynamic> json) => _$MileageReadingFromJson(json);

  @DateOnlyConverter()
  final DateTime date;
  final int mileage;
  @JsonKey(unknownEnumValue: SourceType.unknown)
  final SourceType sourceType;
}

/// A later reading lower than an earlier one. An inconsistency to report, never an accusation of fraud.
@JsonSerializable(createToJson: false)
class MileageWarning {
  const MileageWarning({required this.code, required this.message, required this.earlier, required this.later});

  factory MileageWarning.fromJson(Map<String, dynamic> json) => _$MileageWarningFromJson(json);

  final String code;
  final String message;
  final MileageReading earlier;
  final MileageReading later;
}

/// `GET /vehicles/{id}/mileage`
@JsonSerializable(createToJson: false)
class MileageHistory {
  const MileageHistory({required this.readings, required this.anomalies});

  factory MileageHistory.fromJson(Map<String, dynamic> json) => _$MileageHistoryFromJson(json);

  final List<MileageReading> readings;
  final List<MileageWarning> anomalies;
}

/// Body of `POST /vehicles/{id}/repairs`. Deliberately has no source type or verification status.
class NewRepair {
  const NewRepair({
    required this.eventType,
    required this.eventDate,
    required this.mileage,
    required this.title,
    this.description,
    this.garageId,
    this.parts = const [],
  });

  final RepairEventType eventType;
  final DateTime eventDate;
  final int mileage;
  final String title;
  final String? description;

  /// Record on behalf of this garage (Phase 8b); null = record as the vehicle owner.
  final String? garageId;
  final List<NewPart> parts;

  Map<String, dynamic> toJson() => {
        'eventType': eventType.wireName,
        'eventDate': const DateOnlyConverter().toJson(eventDate),
        'mileage': mileage,
        'title': title,
        if (description != null && description!.isNotEmpty) 'description': description,
        if (garageId != null) 'garageId': garageId,
        'parts': [for (final part in parts) part.toJson()],
      };
}

class NewPart {
  const NewPart({required this.description, required this.quantity, this.partNumber, this.brand});

  final String? partNumber;
  final String? brand;
  final String description;
  final int quantity;

  Map<String, dynamic> toJson() => {
        if (partNumber != null && partNumber!.isNotEmpty) 'partNumber': partNumber,
        if (brand != null && brand!.isNotEmpty) 'brand': brand,
        'description': description,
        'quantity': quantity,
      };
}
