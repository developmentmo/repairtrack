import 'package:json_annotation/json_annotation.dart';

import '../../../core/json/date_only_converter.dart';

part 'dispute.g.dart';

@JsonEnum(fieldRename: FieldRename.screamingSnake)
enum DisputeStatus {
  /// The current owner can still respond.
  open,
  awaitingReview,
  upheld,
  rejected,
  unknown;

  bool get isDecided => this == upheld || this == rejected;
}

@JsonEnum(fieldRename: FieldRename.screamingSnake)
enum DisputeParty { claimant, owner, unknown }

@JsonSerializable(createToJson: false)
class DisputeVehicle {
  const DisputeVehicle({required this.id, required this.make, required this.model, this.licensePlate});

  factory DisputeVehicle.fromJson(Map<String, dynamic> json) => _$DisputeVehicleFromJson(json);

  final String id;
  final String? licensePlate;
  final String make;
  final String model;

  String get displayName => [
        '$make $model',
        if (licensePlate != null) '($licensePlate)',
      ].join(' ');
}

@JsonSerializable(createToJson: false)
class DisputeEvidence {
  const DisputeEvidence({
    required this.id,
    required this.party,
    required this.fileName,
    required this.mimeType,
    required this.fileSize,
    required this.uploadedAt,
  });

  factory DisputeEvidence.fromJson(Map<String, dynamic> json) => _$DisputeEvidenceFromJson(json);

  final String id;
  @JsonKey(unknownEnumValue: DisputeParty.unknown)
  final DisputeParty party;
  final String fileName;
  final String mimeType;
  final int fileSize;
  final DateTime uploadedAt;
}

/// `GET /disputes/mine`: a dispute as one party sees it. Never the other party's identity, statement or files.
@JsonSerializable(createToJson: false)
class PartyDispute {
  const PartyDispute({
    required this.id,
    required this.role,
    required this.status,
    required this.createdAt,
    required this.responseDeadline,
    required this.canRespond,
    required this.canAddEvidence,
    this.vehicle,
    this.myStatement,
    this.myEvidence = const [],
    this.decidedAt,
    this.decisionNote,
    this.newOwnerSince,
  });

  factory PartyDispute.fromJson(Map<String, dynamic> json) => _$PartyDisputeFromJson(json);

  final String id;

  /// CLAIMANT: the caller filed it. OWNER: it is about the caller's ownership.
  @JsonKey(unknownEnumValue: DisputeParty.unknown)
  final DisputeParty role;
  final DisputeVehicle? vehicle;
  @JsonKey(unknownEnumValue: DisputeStatus.unknown)
  final DisputeStatus status;
  final DateTime createdAt;
  final DateTime responseDeadline;
  final bool canRespond;
  final bool canAddEvidence;
  final String? myStatement;
  @JsonKey(defaultValue: <DisputeEvidence>[])
  final List<DisputeEvidence> myEvidence;
  final DateTime? decidedAt;
  final String? decisionNote;
  @NullableDateOnlyConverter()
  final DateTime? newOwnerSince;
}

@JsonSerializable(createToJson: false)
class DisputeParticipant {
  const DisputeParticipant({
    required this.id,
    required this.email,
    required this.firstName,
    required this.lastName,
  });

  factory DisputeParticipant.fromJson(Map<String, dynamic> json) => _$DisputeParticipantFromJson(json);

  final String id;
  final String email;
  final String firstName;
  final String lastName;

  String get displayName => '$firstName $lastName ($email)';
}

/// `GET /admin/disputes`: everything, for system admins.
@JsonSerializable(createToJson: false)
class AdminDispute {
  const AdminDispute({
    required this.id,
    required this.status,
    required this.reviewable,
    required this.claimantStatement,
    required this.createdAt,
    required this.responseDeadline,
    this.vehicle,
    this.claimant,
    this.owner,
    this.ownerStatement,
    this.ownerRespondedAt,
    this.evidence = const [],
    this.decidedAt,
    this.decisionNote,
    this.newOwnerSince,
  });

  factory AdminDispute.fromJson(Map<String, dynamic> json) => _$AdminDisputeFromJson(json);

  final String id;
  final DisputeVehicle? vehicle;
  @JsonKey(unknownEnumValue: DisputeStatus.unknown)
  final DisputeStatus status;

  /// The owner responded or the deadline passed: a decision is possible.
  final bool reviewable;
  final DisputeParticipant? claimant;
  final DisputeParticipant? owner;
  final String claimantStatement;
  final String? ownerStatement;
  final DateTime? ownerRespondedAt;
  final DateTime createdAt;
  final DateTime responseDeadline;
  @JsonKey(defaultValue: <DisputeEvidence>[])
  final List<DisputeEvidence> evidence;
  final DateTime? decidedAt;
  final String? decisionNote;
  @NullableDateOnlyConverter()
  final DateTime? newOwnerSince;
}

/// A file to submit as evidence. The server decides the type from the bytes; the name is only for display.
class EvidenceFile {
  const EvidenceFile({required this.fileName, required this.bytes});

  final String fileName;
  final List<int> bytes;
}

String disputeStatusLabel(DisputeStatus status) => switch (status) {
      DisputeStatus.open => 'Wacht op reactie eigenaar',
      DisputeStatus.awaitingReview => 'In beoordeling',
      DisputeStatus.upheld => 'Toegekend',
      DisputeStatus.rejected => 'Afgewezen',
      DisputeStatus.unknown => 'Onbekend',
    };
