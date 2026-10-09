import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:repairtrack_app/core/network/api_exception.dart';
import 'package:repairtrack_app/features/vehicles/data/vehicle_api.dart';
import 'package:repairtrack_app/features/vehicles/domain/vehicle.dart';
import 'package:repairtrack_app/features/vehicles/presentation/add_vehicle_screen.dart';

class MockVehicleApi extends Mock implements VehicleApi {}

void main() {
  test('parses RDW data; missing fields stay empty', () {
    final full = RegistryVehicle.fromJson({
      'licensePlate': '12ABC3',
      'make': 'VOLKSWAGEN',
      'model': 'GOLF',
      'vehicleType': 'Personenauto',
      'firstRegistrationDate': '2015-03-12',
      'apkExpiryDate': '2027-03-12',
      'primaryColor': 'GRIJS',
      'fuelTypes': ['Benzine', 'Elektriciteit'],
      'source': 'RDW',
    });
    final sparse = RegistryVehicle.fromJson({'licensePlate': 'ZZ999Z', 'fuelTypes': null});

    expect(full.firstRegistrationDate, DateTime(2015, 3, 12));
    expect(full.fuelTypes, ['Benzine', 'Elektriciteit']);
    expect(sparse.make, isNull);
    expect(sparse.fuelTypes, isEmpty);
  });

  group('register form', () {
    late MockVehicleApi api;

    setUp(() => api = MockVehicleApi());

    Future<void> pumpForm(WidgetTester tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [vehicleApiProvider.overrideWithValue(api)],
          child: const MaterialApp(home: AddVehicleScreen()),
        ),
      );
      await tester.pumpAndSettle();
    }

    String fieldText(WidgetTester tester, String key) =>
        tester.widget<TextFormField>(find.byKey(Key(key))).controller!.text;

    testWidgets('fills make, model and model year from the RDW and shows what was found', (tester) async {
      when(() => api.registryLookup(any())).thenAnswer(
        (_) async => RegistryVehicle(
          licensePlate: '12ABC3',
          make: 'VOLKSWAGEN',
          model: 'GOLF',
          firstRegistrationDate: DateTime(2015, 3, 12),
          apkExpiryDate: DateTime(2027, 3, 12),
          fuelTypes: const ['Benzine'],
        ),
      );
      await pumpForm(tester);

      await tester.enterText(find.byKey(const Key('vehicle-plate')), '12-abc-3');
      await tester.tap(find.byKey(const Key('rdw-lookup')));
      await tester.pumpAndSettle();

      verify(() => api.registryLookup('12-abc-3')).called(1);
      expect(fieldText(tester, 'vehicle-make'), 'VOLKSWAGEN');
      expect(fieldText(tester, 'vehicle-model'), 'GOLF');
      expect(fieldText(tester, 'vehicle-year'), '2015');
      expect(find.byKey(const Key('rdw-summary')), findsOneWidget);
      expect(find.textContaining('APK tot 12-03-2027'), findsOneWidget);

      // Another plate: the RDW data no longer applies.
      await tester.enterText(find.byKey(const Key('vehicle-plate')), '99-xyz-9');
      await tester.pump();
      expect(find.byKey(const Key('rdw-summary')), findsNothing);
    });

    testWidgets('an unavailable RDW is not an error: fill in by hand', (tester) async {
      when(() => api.registryLookup(any())).thenThrow(
        const ApiException(statusCode: 503, code: 'REGISTRY_UNAVAILABLE', message: 'down'),
      );
      await pumpForm(tester);

      await tester.enterText(find.byKey(const Key('vehicle-plate')), '12ABC3');
      await tester.tap(find.byKey(const Key('rdw-lookup')));
      await tester.pumpAndSettle();

      expect(find.text('De RDW-gegevens zijn nu niet beschikbaar. Vul de gegevens zelf in.'), findsOneWidget);
      expect(fieldText(tester, 'vehicle-make'), isEmpty);
      expect(fieldText(tester, 'vehicle-year'), isEmpty);
    });
  });
}
