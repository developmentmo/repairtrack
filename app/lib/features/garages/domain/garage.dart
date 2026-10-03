import 'package:json_annotation/json_annotation.dart';

part 'garage.g.dart';

@JsonEnum(fieldRename: FieldRename.screamingSnake)
enum GarageVerificationStatus { unverified, pending, verified, suspended, unknown }

@JsonEnum(fieldRename: FieldRename.screamingSnake)
enum GarageRole { garageAdmin, mechanic, unknown }

/// `GarageResponse`
@JsonSerializable(createToJson: false)
class Garage {
  const Garage({
    required this.id,
    required this.name,
    required this.kvkNumber,
    required this.address,
    required this.postalCode,
    required this.city,
    required this.verificationStatus,
    this.phone,
    this.email,
  });

  factory Garage.fromJson(Map<String, dynamic> json) => _$GarageFromJson(json);

  final String id;
  final String name;
  final String kvkNumber;
  final String address;
  final String postalCode;
  final String city;
  final String? phone;
  final String? email;
  @JsonKey(unknownEnumValue: GarageVerificationStatus.unknown)
  final GarageVerificationStatus verificationStatus;

  /// Suspended garages cannot record work (the backend enforces this too).
  bool get canRecordWork => verificationStatus != GarageVerificationStatus.suspended;
}

/// `GET /garages/mine`: the caller's memberships.
@JsonSerializable(createToJson: false)
class MyGarage {
  const MyGarage({
    required this.garageId,
    required this.name,
    required this.verificationStatus,
    required this.role,
    this.city,
  });

  factory MyGarage.fromJson(Map<String, dynamic> json) => _$MyGarageFromJson(json);

  final String garageId;
  final String name;
  final String? city;
  @JsonKey(unknownEnumValue: GarageVerificationStatus.unknown)
  final GarageVerificationStatus verificationStatus;
  @JsonKey(unknownEnumValue: GarageRole.unknown)
  final GarageRole role;

  bool get isAdmin => role == GarageRole.garageAdmin;
}

/// Body of `POST /garages`. The caller becomes the garage's first admin; the garage starts as PENDING.
class NewGarage {
  const NewGarage({
    required this.name,
    required this.kvkNumber,
    required this.address,
    required this.postalCode,
    required this.city,
    this.phone,
    this.email,
  });

  final String name;
  final String kvkNumber;
  final String address;
  final String postalCode;
  final String city;
  final String? phone;
  final String? email;

  Map<String, dynamic> toJson() => {
        'name': name,
        'kvkNumber': kvkNumber,
        'address': address,
        'postalCode': postalCode,
        'city': city,
        if (phone != null && phone!.isNotEmpty) 'phone': phone,
        if (email != null && email!.isNotEmpty) 'email': email,
      };
}

/// Same rules as the backend: 8 digits.
bool isValidKvkNumber(String value) => RegExp(r'^[0-9]{8}$').hasMatch(value.trim());

/// Dutch postal code: 1234 AB or 1234AB (first digit not 0).
bool isValidPostalCode(String value) => RegExp(r'^[1-9][0-9]{3} ?[A-Za-z]{2}$').hasMatch(value.trim());
