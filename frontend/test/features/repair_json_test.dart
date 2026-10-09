import 'package:flutter_test/flutter_test.dart';
import 'package:repairtrack_app/features/repairs/domain/repair.dart';
import 'package:repairtrack_app/features/vehicles/domain/vehicle.dart';

void main() {
  test('parses a RepairResponse as sent by the backend', () {
    final repair = Repair.fromJson({
      'id': 'r1',
      'vehicleId': 'v1',
      'eventType': 'TYRE_CHANGE',
      'eventDate': '2026-09-14',
      'mileage': 183421,
      'title': 'Winterbanden',
      'description': null,
      'sourceType': 'VERIFIED_GARAGE',
      'verificationStatus': 'GARAGE_VERIFIED',
      'status': 'VOIDED',
      'garage': {'id': 'g1', 'name': 'Garage Test', 'city': 'Utrecht', 'verificationStatus': 'VERIFIED'},
      'parts': [
        {'id': 'p1', 'partNumber': null, 'brand': 'Michelin', 'description': 'Alpin 6', 'quantity': 4},
      ],
      'corrections': [
        {
          'field': 'MILEAGE',
          'originalValue': '18342',
          'correctedValue': '183421',
          'reason': 'Typo',
          'correctedByGarage': null,
          'correctedAt': '2026-09-15T08:00:00Z',
        },
      ],
      'voidedAt': '2026-09-16T08:00:00Z',
      'voidReason': 'Wrong vehicle',
      'createdAt': '2026-09-14T10:00:00Z',
      'updatedAt': '2026-09-16T08:00:00Z',
      'canCorrect': false,
      'canVoid': true,
      'warnings': [],
    });

    expect(repair.eventType, RepairEventType.tyreChange);
    expect(repair.eventDate, DateTime(2026, 9, 14));
    expect(repair.sourceType, SourceType.verifiedGarage);
    expect(repair.verificationStatus, VerificationStatus.garageVerified);
    expect(repair.isVoided, isTrue);
    expect(repair.garage!.name, 'Garage Test');
    expect(repair.parts.single.quantity, 4);
    expect(repair.corrections.single.correctedByGarage, isNull);
    expect(repair.createdAt.isUtc, isTrue);
    expect(repair.canCorrect, isFalse);
    expect(repair.canVoid, isTrue);
  });

  test('unknown enum values from a newer backend do not break parsing', () {
    final reading = MileageReading.fromJson({'date': '2026-01-02', 'mileage': 10, 'sourceType': 'SOMETHING_NEW'});

    expect(reading.sourceType, SourceType.unknown);
  });

  test('a new repair is sent without provenance fields and with a plain date', () {
    final json = NewRepair(
      eventType: RepairEventType.apk,
      eventDate: DateTime(2026, 3, 5),
      mileage: 1000,
      title: 'APK',
      parts: const [NewPart(description: 'Wisserblad', quantity: 2)],
    ).toJson();

    expect(json['eventType'], 'APK');
    expect(json['eventDate'], '2026-03-05');
    expect(json.containsKey('sourceType'), isFalse);
    expect(json.containsKey('verificationStatus'), isFalse);
    expect(json.containsKey('garageId'), isFalse);
    expect(json['parts'], [
      {'description': 'Wisserblad', 'quantity': 2},
    ]);
  });

  test('VIN validation follows the backend rules', () {
    expect(isValidVin('wvwzzz1jzxw000001'), isTrue);
    expect(isValidVin('WVW ZZZ1JZ XW000001'), isTrue);
    expect(isValidVin('WVWZZZ1JZXW00000I'), isFalse);
    expect(isValidVin('TOO-SHORT'), isFalse);
  });
}
