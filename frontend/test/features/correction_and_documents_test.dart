import 'package:flutter_test/flutter_test.dart';
import 'package:repairtrack_app/features/documents/domain/document.dart';
import 'package:repairtrack_app/features/garages/domain/garage.dart';
import 'package:repairtrack_app/features/repairs/domain/repair.dart';

void main() {
  final original = Repair(
    id: 'r1',
    vehicleId: 'v1',
    eventType: RepairEventType.maintenance,
    eventDate: DateTime(2026, 9, 1),
    mileage: 120000,
    title: 'Grote beurt',
    sourceType: SourceType.owner,
    verificationStatus: VerificationStatus.unverified,
    status: RepairStatus.active,
    createdAt: DateTime.utc(2026, 9, 1),
  );

  RepairCorrection? diff({int mileage = 120000, String title = 'Grote beurt', String description = ''}) =>
      RepairCorrection.diff(
        original,
        reason: 'Typfout',
        eventType: RepairEventType.maintenance,
        eventDate: DateTime(2026, 9, 1),
        mileage: mileage,
        title: title,
        description: description,
      );

  group('corrections', () {
    test('only changed fields are sent, with the reason', () {
      expect(diff(mileage: 121000)!.toJson(), {'mileage': 121000, 'reason': 'Typfout'});
    });

    test('nothing changed gives no correction', () {
      expect(diff(), isNull);
    });

    test('an empty description equals a missing one', () {
      expect(diff(title: 'Kleine beurt')!.toJson(), {'title': 'Kleine beurt', 'reason': 'Typfout'});
    });
  });

  group('documents', () {
    test('parses a DocumentResponse', () {
      final document = RepairDocument.fromJson({
        'id': 'd1',
        'repairEventId': 'r1',
        'documentType': 'WORK_ORDER',
        'fileName': 'werkorder.pdf',
        'mimeType': 'application/pdf',
        'fileSize': 2048,
        'sha256': 'ab' * 32,
        'uploadedAt': '2026-09-20T10:00:00Z',
        'repairVerificationRaised': true,
        'downloadUrl': null,
        'downloadUrlExpiresAt': null,
      });

      expect(document.documentType, DocumentType.workOrder);
      expect(document.documentType.isEvidence, isTrue);
      expect(document.repairVerificationRaised, isTrue);
      expect(document.isImage, isFalse);
    });

    test('document types are sent in the backend format', () {
      expect(DocumentType.inspectionReport.wireName, 'INSPECTION_REPORT');
      expect(DocumentType.photo.isEvidence, isFalse);
    });

    test('file sizes are readable', () {
      expect(formatFileSize(512), '512 B');
      expect(formatFileSize(2048), '2 kB');
      expect(formatFileSize(5 * 1024 * 1024 + 300000), '5,3 MB');
    });
  });

  group('garages', () {
    test('parses memberships', () {
      final garage = MyGarage.fromJson({
        'garageId': 'g1',
        'name': 'Garage Test',
        'city': 'Utrecht',
        'verificationStatus': 'PENDING',
        'role': 'GARAGE_ADMIN',
        'memberSince': '2026-09-01T10:00:00Z',
      });

      expect(garage.isAdmin, isTrue);
      expect(garage.verificationStatus, GarageVerificationStatus.pending);
    });

    test('validates KvK numbers and postal codes like the backend', () {
      expect(isValidKvkNumber('12345678'), isTrue);
      expect(isValidKvkNumber('1234567'), isFalse);
      expect(isValidPostalCode('1234 AB'), isTrue);
      expect(isValidPostalCode('1234ab'), isTrue);
      expect(isValidPostalCode('0123 AB'), isFalse);
    });
  });
}
