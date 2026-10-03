import 'package:flutter_test/flutter_test.dart';
import 'package:repairtrack_app/features/documents/domain/document.dart';
import 'package:repairtrack_app/features/repairs/domain/repair.dart';
import 'package:repairtrack_app/features/sharing/domain/public_report.dart';
import 'package:repairtrack_app/features/sharing/domain/share.dart';

void main() {
  test('parses a created share; the link is never printed', () {
    final created = CreatedShare.fromJson({
      'share': {
        'id': 's1',
        'createdAt': '2026-10-01T10:00:00Z',
        'expiresAt': '2026-10-31T10:00:00Z',
        'includeDocuments': false,
        'status': 'OWNER_CHANGED',
        'accessCount': 3,
        'lastAccessedAt': null,
      },
      'token': 'secret-token',
      'url': 'http://localhost:5173/v/secret-token',
    });

    expect(created.share.status, ShareStatus.ownerChanged);
    expect(created.share.isActive, isFalse);
    expect(created.url, endsWith('/v/secret-token'));
    expect(created.toString(), isNot(contains('secret-token')));
  });

  test('parses the public report as sent by the backend', () {
    final report = PublicReport.fromJson({
      'vehicle': {
        'make': 'Volkswagen',
        'model': 'Golf',
        'modelYear': 2015,
        'firstRegistrationDate': '2015-03-01',
        'licensePlate': 'AB123C',
        'registeredOwnerCount': 2,
      },
      'summary': {
        'totalRecords': 2,
        'voidedRecords': 1,
        'recordsByVerification': {'GARAGE_VERIFIED': 1, 'UNVERIFIED': 1},
        'firstEventDate': '2026-01-10',
        'lastEventDate': '2026-09-14',
        'lastRecordedMileage': 183421,
        'mileageInconsistencies': 1,
        'documentCount': 1,
      },
      'history': [
        {
          'eventType': 'MAINTENANCE',
          'eventDate': '2026-09-14',
          'mileage': 183421,
          'title': 'Grote beurt',
          'description': null,
          'sourceType': 'VERIFIED_GARAGE',
          'verificationStatus': 'GARAGE_VERIFIED',
          'voided': false,
          'voidReason': null,
          'garage': {'name': 'Garage Test', 'city': 'Utrecht', 'verificationStatus': 'VERIFIED'},
          'parts': [],
          'corrections': [
            {
              'field': 'MILEAGE',
              'originalValue': '18342',
              'correctedValue': '183421',
              'reason': 'Typfout',
              'correctedBy': 'Garage Test',
              'correctedAt': '2026-09-15T08:00:00Z',
            },
          ],
          'documents': [
            {
              'documentType': 'INVOICE',
              'mimeType': 'application/pdf',
              'fileSize': 1000,
              'uploadedAt': '2026-09-14T12:00:00Z',
              'downloadable': false,
              'reference': null,
            },
          ],
        },
      ],
      'mileage': {
        'readings': [
          {'date': '2026-01-10', 'mileage': 190000, 'sourceType': 'OWNER'},
        ],
        'inconsistencies': [
          {
            'message': 'Lower mileage',
            'earlier': {'date': '2026-01-10', 'mileage': 190000, 'sourceType': 'OWNER'},
            'later': {'date': '2026-09-14', 'mileage': 183421, 'sourceType': 'VERIFIED_GARAGE'},
          },
        ],
      },
      'documentsDownloadable': false,
      'generatedAt': '2026-10-03T10:00:00Z',
      'linkValidUntil': '2026-11-02T10:00:00Z',
    });

    expect(report.vehicle.registeredOwnerCount, 2);
    expect(report.summary.recordsByVerification['GARAGE_VERIFIED'], 1);
    final entry = report.history.single;
    expect(entry.sourceType, SourceType.verifiedGarage);
    expect(entry.corrections.single.correctedBy, 'Garage Test');
    expect(entry.documents.single.documentType, DocumentType.invoice);
    expect(entry.documents.single.reference, isNull);
    expect(report.mileage.inconsistencies.single.later.mileage, 183421);
  });
}
