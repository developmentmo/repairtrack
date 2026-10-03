import 'package:json_annotation/json_annotation.dart';

import '../../../core/json/date_only_converter.dart';

part 'vehicle.g.dart';

/// `VehicleResponse`. Never contains owner identity.
@JsonSerializable(createToJson: false)
class Vehicle {
  const Vehicle({
    required this.id,
    required this.make,
    required this.model,
    required this.ownedByMe,
    required this.canEdit,
    this.vin,
    this.licensePlate,
    this.modelYear,
    this.firstRegistrationDate,
  });

  factory Vehicle.fromJson(Map<String, dynamic> json) => _$VehicleFromJson(json);

  final String id;

  /// Only visible to the current owner, the registering garage and system admins.
  final String? vin;
  final String? licensePlate;
  final String make;
  final String model;
  final int? modelYear;
  @NullableDateOnlyConverter()
  final DateTime? firstRegistrationDate;
  final bool ownedByMe;
  final bool canEdit;

  String get displayName => '$make $model';
}

/// `GET /vehicles/search`: exact match, never contains a VIN.
@JsonSerializable(createToJson: false)
class VehicleSearchResult {
  const VehicleSearchResult({
    required this.id,
    required this.make,
    required this.model,
    this.licensePlate,
    this.modelYear,
  });

  factory VehicleSearchResult.fromJson(Map<String, dynamic> json) => _$VehicleSearchResultFromJson(json);

  final String id;
  final String? licensePlate;
  final String make;
  final String model;
  final int? modelYear;

  String get displayName => '$make $model';
}

/// `GET /vehicle-registry/{plate}`: public RDW data to pre-fill the form. A suggestion the user checks,
/// never proof of ownership.
@JsonSerializable(createToJson: false)
class RegistryVehicle {
  const RegistryVehicle({
    required this.licensePlate,
    this.make,
    this.model,
    this.vehicleType,
    this.firstRegistrationDate,
    this.apkExpiryDate,
    this.primaryColor,
    this.fuelTypes = const [],
  });

  factory RegistryVehicle.fromJson(Map<String, dynamic> json) => _$RegistryVehicleFromJson(json);

  final String licensePlate;
  final String? make;

  /// Trade name, e.g. "GOLF".
  final String? model;
  final String? vehicleType;

  /// First admission anywhere (for imported cars: abroad).
  @NullableDateOnlyConverter()
  final DateTime? firstRegistrationDate;

  /// Expiry of the Dutch periodic inspection (APK).
  @NullableDateOnlyConverter()
  final DateTime? apkExpiryDate;
  final String? primaryColor;
  @JsonKey(defaultValue: <String>[])
  final List<String> fuelTypes;
}

/// Body of `POST /vehicles`.
class NewVehicle {
  const NewVehicle({
    required this.vin,
    required this.make,
    required this.model,
    this.licensePlate,
    this.modelYear,
    this.firstRegistrationDate,
    this.ownedSince,
    this.garageId,
  });

  final String vin;
  final String make;
  final String model;
  final String? licensePlate;
  final int? modelYear;
  final DateTime? firstRegistrationDate;
  final DateTime? ownedSince;

  /// Register on behalf of a garage (Phase 8b); null = the caller becomes the owner.
  final String? garageId;

  Map<String, dynamic> toJson() {
    const date = NullableDateOnlyConverter();
    return {
      'vin': vin,
      'make': make,
      'model': model,
      if (licensePlate != null) 'licensePlate': licensePlate,
      if (modelYear != null) 'modelYear': modelYear,
      if (firstRegistrationDate != null) 'firstRegistrationDate': date.toJson(firstRegistrationDate),
      if (ownedSince != null) 'ownedSince': date.toJson(ownedSince),
      if (garageId != null) 'garageId': garageId,
    };
  }
}

/// VIN rules from the backend: 17 characters, letters and digits except I, O and Q.
/// Case and spaces are ignored.
String normalizeVin(String input) => input.replaceAll(RegExp(r'\s'), '').toUpperCase();

bool isValidVin(String input) => RegExp(r'^[A-HJ-NPR-Z0-9]{17}$').hasMatch(normalizeVin(input));
