import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:repairtrack_app/core/network/api_exception.dart';
import 'package:repairtrack_app/core/network/dio_provider.dart';
import 'package:repairtrack_app/core/widgets/app_shell.dart';
import 'package:repairtrack_app/features/disputes/data/dispute_api.dart';
import 'package:repairtrack_app/features/disputes/domain/dispute.dart';
import 'package:repairtrack_app/features/garages/data/garage_api.dart';
import 'package:repairtrack_app/features/garages/domain/garage.dart';
import 'package:repairtrack_app/features/repairs/data/repair_api.dart';
import 'package:repairtrack_app/features/repairs/domain/repair.dart';
import 'package:repairtrack_app/features/repairs/presentation/repair_labels.dart';
import 'package:repairtrack_app/features/vehicles/application/dashboard_providers.dart';
import 'package:repairtrack_app/features/vehicles/data/vehicle_api.dart';
import 'package:repairtrack_app/features/vehicles/domain/vehicle.dart';
import 'package:repairtrack_app/features/vehicles/presentation/owner_dashboard_screen.dart';

import '../helpers/in_memory_token_store.dart';

class MockVehicleApi extends Mock implements VehicleApi {}

class MockRepairApi extends Mock implements RepairApi {}

class MockGarageApi extends Mock implements GarageApi {}

class MockDisputeApi extends Mock implements DisputeApi {}

const _golf = Vehicle(id: 'v1', make: 'Volkswagen', model: 'Golf', ownedByMe: true, canEdit: true, licensePlate: 'K-123-AB');
const _bmw = Vehicle(id: 'v2', make: 'BMW', model: '3 Serie', ownedByMe: true, canEdit: true);

Repair _repair(String id, String vehicleId, DateTime date, {String title = 'Onderhoud', bool voided = false}) => Repair(
      id: id,
      vehicleId: vehicleId,
      eventType: RepairEventType.maintenance,
      eventDate: date,
      mileage: 48231,
      title: title,
      sourceType: SourceType.verifiedGarage,
      verificationStatus: VerificationStatus.garageVerified,
      status: voided ? RepairStatus.voided : RepairStatus.active,
      createdAt: date,
    );

void main() {
  late MockVehicleApi vehicles;
  late MockRepairApi repairs;
  late MockGarageApi garages;
  late MockDisputeApi disputes;

  setUp(() {
    vehicles = MockVehicleApi();
    repairs = MockRepairApi();
    garages = MockGarageApi();
    disputes = MockDisputeApi();
    when(() => garages.mine()).thenAnswer((_) async => const <MyGarage>[]);
    when(() => disputes.mine()).thenAnswer((_) async => const <PartyDispute>[]);
  });

  List<Override> overrides() => [
        vehicleApiProvider.overrideWithValue(vehicles),
        repairApiProvider.overrideWithValue(repairs),
        garageApiProvider.overrideWithValue(garages),
        disputeApiProvider.overrideWithValue(disputes),
        tokenStoreProvider.overrideWithValue(InMemoryTokenStore()),
      ];

  group('dashboardRepairsProvider', () {
    test('merges the histories of all vehicles, newest first, without voided records', () async {
      when(() => vehicles.myVehicles()).thenAnswer((_) async => [_golf, _bmw]);
      when(() => repairs.history('v1')).thenAnswer((_) async => [
            _repair('r1', 'v1', DateTime(2025, 3, 12)),
            _repair('r2', 'v1', DateTime(2024, 1, 5), voided: true),
          ]);
      when(() => repairs.history('v2')).thenAnswer((_) async => [_repair('r3', 'v2', DateTime(2025, 6, 1))]);
      final container = ProviderContainer(overrides: overrides());
      addTearDown(container.dispose);

      final result = await container.read(dashboardRepairsProvider.future);

      expect(result.map((r) => r.repair.id), ['r3', 'r1']);
      expect(result.first.vehicle.id, 'v2');
    });

    test('a vehicle whose history fails is skipped instead of failing the dashboard', () async {
      when(() => vehicles.myVehicles()).thenAnswer((_) async => [_golf, _bmw]);
      when(() => repairs.history('v1')).thenAnswer((_) async => [_repair('r1', 'v1', DateTime(2025, 3, 12))]);
      when(() => repairs.history('v2'))
          .thenThrow(const ApiException(statusCode: 500, code: 'INTERNAL', message: 'boom'));
      final container = ProviderContainer(overrides: overrides());
      addTearDown(container.dispose);

      final result = await container.read(dashboardRepairsProvider.future);

      expect(result.map((r) => r.repair.id), ['r1']);
    });
  });

  testWidgets('the dashboard shows counts, vehicles and recent repairs', (tester) async {
    when(() => vehicles.myVehicles()).thenAnswer((_) async => [_golf, _bmw]);
    when(() => repairs.history('v1'))
        .thenAnswer((_) async => [_repair('r1', 'v1', DateTime(2025, 3, 12), title: 'Grote onderhoudsbeurt')]);
    when(() => repairs.history('v2')).thenAnswer((_) async => const <Repair>[]);

    await tester.pumpWidget(
      ProviderScope(overrides: overrides(), child: const MaterialApp(home: OwnerDashboardScreen())),
    );
    await tester.pumpAndSettle();

    expect(find.text('Volkswagen Golf'), findsOneWidget);
    expect(find.text('K-123-AB'), findsOneWidget);
    expect(find.text('Voertuigen'), findsOneWidget);
    expect(find.text('Alles up-to-date'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Grote onderhoudsbeurt'), 200);
    expect(find.text('Grote onderhoudsbeurt'), findsOneWidget);
    expect(find.text('Garage bevestigd'), findsOneWidget);
  });

  group('AppShell', () {
    Future<void> pumpShell(WidgetTester tester, Size size) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        ProviderScope(
          overrides: overrides(),
          child: const MaterialApp(home: AppShell(location: '/', child: Text('inhoud'))),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('a phone gets a bottom navigation bar', (tester) async {
      await pumpShell(tester, const Size(400, 800));

      expect(find.byType(NavigationBar), findsOneWidget);
      expect(find.text('Uitloggen'), findsNothing);
    });

    testWidgets('a wide screen gets the sidebar with logout', (tester) async {
      await pumpShell(tester, const Size(1400, 900));

      expect(find.byType(NavigationBar), findsNothing);
      expect(find.text('Dashboard'), findsOneWidget);
      expect(find.text('Garage aanmelden'), findsOneWidget);
      expect(find.text('Uitloggen'), findsOneWidget);
    });
  });

  test('verification labels match the design', () {
    expect(verificationLabel(VerificationStatus.garageVerified), 'Garage bevestigd');
    expect(verificationLabel(VerificationStatus.officialSource), 'Officiële bron');
    expect(verificationLabel(VerificationStatus.unverified), 'Onbevestigd');
  });
}
