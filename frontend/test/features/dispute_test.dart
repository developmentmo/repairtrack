import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:repairtrack_app/features/disputes/domain/dispute.dart';
import 'package:repairtrack_app/features/disputes/presentation/my_disputes_screen.dart';
import 'package:repairtrack_app/features/repairs/domain/repair.dart';
import 'package:repairtrack_app/features/sharing/domain/public_report.dart';

Map<String, dynamic> _partyJson({required String role, required bool canRespond, String status = 'OPEN'}) => {
      'id': 'd1',
      'role': role,
      'vehicle': {'id': 'v1', 'licensePlate': '12ABC3', 'make': 'Volkswagen', 'model': 'Golf'},
      'status': status,
      'createdAt': '2026-10-01T10:00:00Z',
      'responseDeadline': '2026-10-15T10:00:00Z',
      'canRespond': canRespond,
      'canAddEvidence': status == 'OPEN' || status == 'AWAITING_REVIEW',
      'myStatement': null,
      'myEvidence': <Object>[],
      'decidedAt': null,
      'decisionNote': null,
      'newOwnerSince': null,
    };

void main() {
  test('parses a dispute as a party and as an admin', () {
    final party = PartyDispute.fromJson(_partyJson(role: 'OWNER', canRespond: true, status: 'AWAITING_REVIEW'));
    expect(party.role, DisputeParty.owner);
    expect(party.status, DisputeStatus.awaitingReview);
    expect(party.vehicle!.displayName, 'Volkswagen Golf (12ABC3)');

    final admin = AdminDispute.fromJson({
      'id': 'd1',
      'vehicle': null,
      'status': 'UPHELD',
      'reviewable': false,
      'claimant': {'id': 'u1', 'email': 'piet@example.nl', 'firstName': 'Piet', 'lastName': 'Jansen'},
      'owner': null,
      'claimantStatement': 'Ik heb hem gekocht.',
      'ownerStatement': null,
      'ownerRespondedAt': null,
      'createdAt': '2026-10-01T10:00:00Z',
      'responseDeadline': '2026-10-15T10:00:00Z',
      'evidence': [
        {
          'id': 'e1',
          'party': 'CLAIMANT',
          'fileName': 'koopcontract.pdf',
          'mimeType': 'application/pdf',
          'fileSize': 1234,
          'sha256': 'ab',
          'uploadedAt': '2026-10-01T10:00:00Z',
        },
      ],
      'decidedBy': 'a1',
      'decidedAt': '2026-10-16T10:00:00Z',
      'decisionNote': 'Contract is overtuigend.',
      'newOwnerSince': '2025-01-01',
    });
    expect(admin.status.isDecided, isTrue);
    expect(admin.newOwnerSince, DateTime(2025, 1, 1));
    expect(admin.evidence.single.party, DisputeParty.claimant);
  });

  test('records and reports carry the revoked-ownership label; older responses default to false', () {
    final repair = Repair.fromJson({
      'id': 'r1',
      'vehicleId': 'v1',
      'eventType': 'MAINTENANCE',
      'eventDate': '2026-09-01',
      'mileage': 1000,
      'title': 'Banden',
      'sourceType': 'OWNER',
      'verificationStatus': 'UNVERIFIED',
      'status': 'ACTIVE',
      'createdAt': '2026-09-01T10:00:00Z',
      'enteredDuringRevokedOwnership': true,
    });
    expect(repair.enteredDuringRevokedOwnership, isTrue);

    final vehicle = PublicVehicle.fromJson({'make': 'Volkswagen', 'model': 'Golf', 'registeredOwnerCount': 1});
    expect(vehicle.ownershipUnderReview, isFalse);
  });

  testWidgets('only the contested owner gets the respond button', (tester) async {
    Future<void> pump(PartyDispute dispute) => tester.pumpWidget(
          ProviderScope(
            child: MaterialApp(home: Scaffold(body: DisputeCard(dispute: dispute))),
          ),
        );

    await pump(PartyDispute.fromJson(_partyJson(role: 'OWNER', canRespond: true)));
    expect(find.text('Iemand betwist dat jij de eigenaar bent.'), findsOneWidget);
    expect(find.byKey(const Key('dispute-respond-d1')), findsOneWidget);

    await pump(PartyDispute.fromJson(_partyJson(role: 'CLAIMANT', canRespond: false)));
    expect(find.text('Jij betwist het eigendom van dit voertuig.'), findsOneWidget);
    expect(find.byKey(const Key('dispute-respond-d1')), findsNothing);
  });
}
