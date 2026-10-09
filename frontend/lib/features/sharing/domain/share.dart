import 'package:json_annotation/json_annotation.dart';

part 'share.g.dart';

@JsonEnum(fieldRename: FieldRename.screamingSnake)
enum ShareStatus { active, expired, revoked, ownerChanged, unknown }

/// `ShareResponse`: a share link without its token (the server only keeps a hash).
@JsonSerializable(createToJson: false)
class VehicleShare {
  const VehicleShare({
    required this.id,
    required this.createdAt,
    required this.expiresAt,
    required this.includeDocuments,
    required this.status,
    required this.accessCount,
    this.lastAccessedAt,
  });

  factory VehicleShare.fromJson(Map<String, dynamic> json) => _$VehicleShareFromJson(json);

  final String id;
  final DateTime createdAt;
  final DateTime expiresAt;
  final bool includeDocuments;
  @JsonKey(unknownEnumValue: ShareStatus.unknown)
  final ShareStatus status;

  /// How often the public report was opened through this link.
  final int accessCount;
  final DateTime? lastAccessedAt;

  bool get isActive => status == ShareStatus.active;
}

/// `POST /vehicles/{id}/shares`: the only moment the link (with its token) is known.
@JsonSerializable(createToJson: false)
class CreatedShare {
  const CreatedShare({required this.share, required this.url});

  factory CreatedShare.fromJson(Map<String, dynamic> json) => _$CreatedShareFromJson(json);

  final VehicleShare share;

  /// `{PUBLIC_BASE_URL}/v/{token}`. Not stored anywhere; show it once and let the owner copy it.
  final String url;

  @override
  String toString() => 'CreatedShare(${share.id}, ***)';
}

/// Validity options offered in the app (the backend accepts 1–365 days).
const shareValidityOptions = [7, 30, 90, 365];
