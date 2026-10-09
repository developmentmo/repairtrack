import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:image_picker/image_picker.dart';
import 'package:mocktail/mocktail.dart';
import 'package:repairtrack_app/core/network/api_exception.dart';
import 'package:repairtrack_app/core/network/error_messages.dart';
import 'package:repairtrack_app/features/disputes/data/dispute_api.dart';
import 'package:repairtrack_app/features/disputes/domain/dispute.dart';
import 'package:repairtrack_app/features/repairs/data/repair_api.dart';
import 'package:repairtrack_app/features/repairs/domain/repair.dart';
import 'package:repairtrack_app/features/vehicles/application/vehicle_providers.dart';
import 'package:repairtrack_app/features/vehicles/data/vehicle_api.dart';
import 'package:repairtrack_app/features/vehicles/domain/vehicle.dart';
import 'package:repairtrack_app/features/vehicles/domain/vehicle_photo.dart';
import 'package:repairtrack_app/features/vehicles/presentation/vehicle_detail_screen.dart';
import 'package:repairtrack_app/features/vehicles/presentation/vehicle_photo_card.dart';

import '../helpers/fake_http_adapter.dart';

class MockVehicleApi extends Mock implements VehicleApi {}

class MockRepairApi extends Mock implements RepairApi {}

class MockDisputeApi extends Mock implements DisputeApi {}

class MockImagePicker extends Mock implements ImagePicker {}

Map<String, dynamic> _photoJson({String id = 'p1', String url = 'https://storage.test/p1.jpg'}) => {
      'id': id,
      'vehicleId': 'v1',
      'mimeType': 'image/jpeg',
      'fileSize': 482113,
      'sha256': 'ab' * 32,
      'uploadedAt': '2026-10-09T10:00:00Z',
      'downloadUrl': url,
      'downloadUrlExpiresAt': '2026-10-09T10:05:00Z',
    };

VehiclePhoto _photo({String id = 'p1', String url = 'https://storage.test/p1.jpg'}) =>
    VehiclePhoto.fromJson(_photoJson(id: id, url: url));

/// A picked image as the platform returns it: with a path, so it has a name.
XFile _picked(Uint8List bytes, String name) => XFile.fromData(bytes, path: '/tmp/$name', name: name);

/// Failed requests are not retried automatically, as in `main.dart`.
Duration? _noRetry(int retryCount, Object error) => null;

Finder _networkImage(String url) =>
    find.byWidgetPredicate((w) => w is Image && w.image is NetworkImage && (w.image as NetworkImage).url == url);

void main() {
  setUpAll(() {
    registerFallbackValue(ImageSource.gallery);
    registerFallbackValue(CameraDevice.rear);
    registerFallbackValue(Uint8List(0));
  });

  group('VehicleApi photo', () {
    late FakeHttpAdapter adapter;
    late VehicleApi api;
    late ResponseBody Function(RequestOptions request) respond;

    setUp(() {
      adapter = FakeHttpAdapter((request) async => respond(request));
      api = VehicleApi(Dio(BaseOptions(baseUrl: 'http://test'))..httpClientAdapter = adapter);
    });

    test('parses the current photo', () async {
      respond = (_) => jsonResponse(_photoJson());

      final photo = await api.photo('v1');

      expect(adapter.requests.single.path, '/api/v1/vehicles/v1/photo');
      expect(photo!.downloadUrl, 'https://storage.test/p1.jpg');
      expect(photo.uploadedAt, DateTime.utc(2026, 10, 9, 10));
      expect(photo.fileSize, 482113);
    });

    test('no photo yet (404 VEHICLE_PHOTO_NOT_FOUND) is null, not an error', () async {
      respond = (_) => errorResponse(404, 'VEHICLE_PHOTO_NOT_FOUND');

      expect(await api.photo('v1'), isNull);
    });

    test('other errors are thrown', () async {
      respond = (_) => errorResponse(403, 'VEHICLE_ACCESS_DENIED');

      expect(
        api.photo('v1'),
        throwsA(isA<ApiException>().having((e) => e.code, 'code', 'VEHICLE_ACCESS_DENIED')),
      );
    });

    test('uploads the file as the multipart part "file"', () async {
      respond = (_) => jsonResponse(_photoJson(id: 'p2'), status: 201);

      final photo = await api.uploadPhoto('v1', fileName: 'golf.jpg', bytes: Uint8List.fromList([1, 2, 3]));

      final request = adapter.requests.single;
      expect(request.method, 'POST');
      expect(request.path, '/api/v1/vehicles/v1/photo');
      final form = request.data as FormData;
      expect(form.files.single.key, 'file');
      expect(form.files.single.value.filename, 'golf.jpg');
      expect(form.fields, isEmpty);
      expect(photo.id, 'p2');
    });
  });

  test('photo error codes have their own Dutch messages', () {
    String message(String code) => vehiclePhotoMessage(ApiException(code: code, message: 'test'));

    expect(message('VEHICLE_ACCESS_DENIED'), 'Alleen de huidige eigenaar kan een foto toevoegen of bekijken.');
    expect(message('VEHICLE_NOT_FOUND'), 'Dit voertuig bestaat niet (meer).');
    expect(message('EMPTY_FILE'), 'Het gekozen bestand is leeg.');
    expect(message('FILE_TOO_LARGE'), 'De foto is te groot (maximaal 20 MB).');
    expect(message('UNSUPPORTED_FILE_TYPE'), "Alleen JPEG-, PNG- en WebP-foto's zijn toegestaan.");
    expect(message('MALWARE_DETECTED'), 'Deze foto is geweigerd omdat er schadelijke inhoud in is gevonden.');
    expect(message('SCANNER_UNAVAILABLE'), 'De foto kan nu niet worden gecontroleerd. Probeer het later opnieuw.');
    expect(message('VEHICLE_PHOTO_CONFLICT'), 'Er werd tegelijk een andere foto geüpload. Probeer het opnieuw.');
    expect(message(ApiException.network), userMessage(const ApiException(code: ApiException.network, message: '')));
  });

  group('VehiclePhotoCard', () {
    late MockVehicleApi api;
    late MockImagePicker picker;
    late VehiclePhoto? current;

    setUp(() {
      api = MockVehicleApi();
      picker = MockImagePicker();
      current = null;
      when(() => api.photo('v1')).thenAnswer((_) async => current);
      when(() => picker.supportsImageSource(any())).thenReturn(true);
    });

    void pickReturns(XFile? file) {
      when(
        () => picker.pickImage(
          source: any(named: 'source'),
          maxWidth: any(named: 'maxWidth'),
          maxHeight: any(named: 'maxHeight'),
          imageQuality: any(named: 'imageQuality'),
          preferredCameraDevice: any(named: 'preferredCameraDevice'),
          requestFullMetadata: any(named: 'requestFullMetadata'),
        ),
      ).thenAnswer((_) async => file);
    }

    void uploadAnswers(Future<VehiclePhoto> Function(Invocation invocation) answer) {
      when(
        () => api.uploadPhoto(
          'v1',
          fileName: any(named: 'fileName'),
          bytes: any(named: 'bytes'),
          onSendProgress: any(named: 'onSendProgress'),
        ),
      ).thenAnswer(answer);
    }

    Future<void> pumpCard(WidgetTester tester) async {
      await tester.pumpWidget(
        ProviderScope(
          retry: _noRetry,
          overrides: [
            vehicleApiProvider.overrideWithValue(api),
            imagePickerProvider.overrideWithValue(picker),
          ],
          child: const MaterialApp(
            home: Scaffold(body: SingleChildScrollView(child: VehiclePhotoCard(vehicleId: 'v1'))),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    Future<void> chooseGallery(WidgetTester tester, String button) async {
      await tester.tap(find.text(button));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Kies uit galerij'));
    }

    testWidgets('without a photo: placeholder and "Foto toevoegen", no error', (tester) async {
      await pumpCard(tester);

      expect(find.byKey(const Key('vehicle-photo-placeholder')), findsOneWidget);
      expect(find.text('Foto toevoegen'), findsOneWidget);
      expect(find.text('Foto vervangen'), findsNothing);
      expect(find.byType(SnackBar), findsNothing);
    });

    testWidgets('with a photo: shows it and offers "Foto vervangen"', (tester) async {
      current = _photo();
      await pumpCard(tester);

      expect(_networkImage('https://storage.test/p1.jpg'), findsOneWidget);
      expect(find.byKey(const Key('vehicle-photo-placeholder')), findsNothing);
      expect(find.text('Foto vervangen'), findsOneWidget);
      expect(find.text('Foto toevoegen'), findsNothing);
    });

    testWidgets('adding a photo uploads it with progress and then shows it', (tester) async {
      pickReturns(_picked(Uint8List.fromList([1, 2, 3]), 'golf.jpg'));
      final upload = Completer<void>();
      uploadAnswers((invocation) async {
        final progress = invocation.namedArguments[#onSendProgress] as void Function(int, int);
        progress(1, 2);
        await upload.future;
        current = _photo(id: 'p2', url: 'https://storage.test/p2.jpg');
        return current!;
      });
      await pumpCard(tester);

      await chooseGallery(tester, 'Foto toevoegen');
      await tester.pump();
      await tester.pump();

      final indicator = tester.widget<CircularProgressIndicator>(find.byType(CircularProgressIndicator));
      expect(indicator.value, 0.5);
      upload.complete();
      await tester.pumpAndSettle();

      verify(
        () => picker.pickImage(
          source: ImageSource.gallery,
          maxWidth: any(named: 'maxWidth'),
          maxHeight: any(named: 'maxHeight'),
          imageQuality: any(named: 'imageQuality'),
          preferredCameraDevice: any(named: 'preferredCameraDevice'),
          requestFullMetadata: false,
        ),
      ).called(1);
      final sent = verify(
        () => api.uploadPhoto(
          'v1',
          fileName: 'golf.jpg',
          bytes: captureAny(named: 'bytes'),
          onSendProgress: any(named: 'onSendProgress'),
        ),
      ).captured.single as Uint8List;
      expect(sent, [1, 2, 3]);
      expect(find.byType(CircularProgressIndicator), findsNothing);
      expect(_networkImage('https://storage.test/p2.jpg'), findsOneWidget);
      expect(find.text('Foto vervangen'), findsOneWidget);
      expect(find.text('Foto opgeslagen.'), findsOneWidget);
    });

    testWidgets('replacing a photo shows the new one', (tester) async {
      current = _photo();
      pickReturns(_picked(Uint8List.fromList([4, 5, 6]), 'nieuw.png'));
      uploadAnswers((_) async {
        current = _photo(id: 'p2', url: 'https://storage.test/p2.png');
        return current!;
      });
      await pumpCard(tester);

      await chooseGallery(tester, 'Foto vervangen');
      await tester.pumpAndSettle();

      expect(_networkImage('https://storage.test/p1.jpg'), findsNothing);
      expect(_networkImage('https://storage.test/p2.png'), findsOneWidget);
    });

    testWidgets('the camera can be chosen', (tester) async {
      pickReturns(null);
      await pumpCard(tester);

      await tester.tap(find.text('Foto toevoegen'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Maak een foto'));
      await tester.pumpAndSettle();

      verify(
        () => picker.pickImage(
          source: ImageSource.camera,
          maxWidth: any(named: 'maxWidth'),
          maxHeight: any(named: 'maxHeight'),
          imageQuality: any(named: 'imageQuality'),
          preferredCameraDevice: any(named: 'preferredCameraDevice'),
          requestFullMetadata: any(named: 'requestFullMetadata'),
        ),
      ).called(1);
      verifyNever(
        () => api.uploadPhoto(
          any(),
          fileName: any(named: 'fileName'),
          bytes: any(named: 'bytes'),
          onSendProgress: any(named: 'onSendProgress'),
        ),
      );
    });

    testWidgets('without a camera the gallery opens directly', (tester) async {
      when(() => picker.supportsImageSource(ImageSource.camera)).thenReturn(false);
      pickReturns(null);
      await pumpCard(tester);

      await tester.tap(find.text('Foto toevoegen'));
      await tester.pumpAndSettle();

      expect(find.text('Maak een foto'), findsNothing);
      verify(
        () => picker.pickImage(
          source: ImageSource.gallery,
          maxWidth: any(named: 'maxWidth'),
          maxHeight: any(named: 'maxHeight'),
          imageQuality: any(named: 'imageQuality'),
          preferredCameraDevice: any(named: 'preferredCameraDevice'),
          requestFullMetadata: any(named: 'requestFullMetadata'),
        ),
      ).called(1);
    });

    for (final (code, status, text) in [
      ('UNSUPPORTED_FILE_TYPE', 415, "Alleen JPEG-, PNG- en WebP-foto's zijn toegestaan."),
      ('MALWARE_DETECTED', 422, 'Deze foto is geweigerd omdat er schadelijke inhoud in is gevonden.'),
      ('VEHICLE_PHOTO_CONFLICT', 409, 'Er werd tegelijk een andere foto geüpload. Probeer het opnieuw.'),
    ]) {
      testWidgets('an upload failing with $code shows the Dutch message', (tester) async {
        pickReturns(_picked(Uint8List.fromList([1]), 'foto.heic'));
        uploadAnswers((_) async => throw ApiException(statusCode: status, code: code, message: 'test'));
        await pumpCard(tester);

        await chooseGallery(tester, 'Foto toevoegen');
        await tester.pumpAndSettle();

        expect(find.text(text), findsOneWidget);
        expect(find.byKey(const Key('vehicle-photo-placeholder')), findsOneWidget);
        expect(find.text('Foto toevoegen'), findsOneWidget);
      });
    }

    testWidgets('a photo larger than 20 MB is not uploaded', (tester) async {
      pickReturns(_picked(Uint8List(maxVehiclePhotoBytes + 1), 'groot.jpg'));
      await pumpCard(tester);

      await chooseGallery(tester, 'Foto toevoegen');
      await tester.pumpAndSettle();

      expect(find.text('De foto is te groot (maximaal 20 MB).'), findsOneWidget);
      verifyNever(
        () => api.uploadPhoto(
          any(),
          fileName: any(named: 'fileName'),
          bytes: any(named: 'bytes'),
          onSendProgress: any(named: 'onSendProgress'),
        ),
      );
    });

    testWidgets('no access to the camera or library is explained', (tester) async {
      when(
        () => picker.pickImage(
          source: any(named: 'source'),
          maxWidth: any(named: 'maxWidth'),
          maxHeight: any(named: 'maxHeight'),
          imageQuality: any(named: 'imageQuality'),
          preferredCameraDevice: any(named: 'preferredCameraDevice'),
          requestFullMetadata: any(named: 'requestFullMetadata'),
        ),
      ).thenThrow(PlatformException(code: 'camera_access_denied'));
      await pumpCard(tester);

      await chooseGallery(tester, 'Foto toevoegen');
      await tester.pumpAndSettle();

      expect(
        find.text('De camera of fotobibliotheek kan niet worden geopend. Controleer de toegang in je instellingen.'),
        findsOneWidget,
      );
    });

    testWidgets('a failing load shows the Dutch message and can be retried', (tester) async {
      var calls = 0;
      when(() => api.photo('v1')).thenAnswer((_) async {
        calls++;
        if (calls == 1) {
          throw const ApiException(statusCode: 403, code: 'VEHICLE_ACCESS_DENIED', message: 'test');
        }
        return _photo();
      });
      await pumpCard(tester);

      expect(find.text('Alleen de huidige eigenaar kan een foto toevoegen of bekijken.'), findsOneWidget);
      await tester.tap(find.text('Opnieuw proberen'));
      await tester.pumpAndSettle();

      expect(_networkImage('https://storage.test/p1.jpg'), findsOneWidget);
    });
  });

  group('vehicle detail screen', () {
    late MockVehicleApi vehicles;
    late MockRepairApi repairs;
    late MockDisputeApi disputes;

    setUp(() {
      vehicles = MockVehicleApi();
      repairs = MockRepairApi();
      disputes = MockDisputeApi();
      when(() => repairs.history(any())).thenAnswer((_) async => const <Repair>[]);
      when(() => disputes.mine()).thenAnswer((_) async => const <PartyDispute>[]);
      when(() => vehicles.photo(any())).thenAnswer((_) async => _photo());
    });

    List<Override> overrides() => [
          vehicleApiProvider.overrideWithValue(vehicles),
          repairApiProvider.overrideWithValue(repairs),
          disputeApiProvider.overrideWithValue(disputes),
          imagePickerProvider.overrideWithValue(MockImagePicker()),
        ];

    Future<void> pumpDetail(WidgetTester tester, Vehicle vehicle, {String? garageId}) async {
      when(() => vehicles.vehicle('v1')).thenAnswer((_) async => vehicle);
      await tester.pumpWidget(
        ProviderScope(
          retry: _noRetry,
          overrides: overrides(),
          child: MaterialApp(home: VehicleDetailScreen(vehicleId: 'v1', garageId: garageId)),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('the owner sees the photo card', (tester) async {
      await pumpDetail(tester, const Vehicle(id: 'v1', make: 'Volkswagen', model: 'Golf', ownedByMe: true, canEdit: true));

      expect(find.byType(VehiclePhotoCard), findsOneWidget);
      expect(_networkImage('https://storage.test/p1.jpg'), findsOneWidget);
    });

    testWidgets('others see no photo and no photo buttons', (tester) async {
      await pumpDetail(
        tester,
        const Vehicle(id: 'v1', make: 'Volkswagen', model: 'Golf', ownedByMe: false, canEdit: false),
        garageId: 'g1',
      );

      expect(find.byType(VehiclePhotoCard), findsNothing);
      expect(find.text('Foto toevoegen'), findsNothing);
      expect(find.text('Foto vervangen'), findsNothing);
      verifyNever(() => vehicles.photo(any()));
    });
  });
}
