import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:repairtrack_app/core/network/api_exception.dart';
import 'package:repairtrack_app/core/network/dio_provider.dart';
import 'package:repairtrack_app/features/authentication/data/auth_api.dart';
import 'package:repairtrack_app/features/authentication/presentation/login_screen.dart';

import '../helpers/in_memory_token_store.dart';

class MockAuthApi extends Mock implements AuthApi {}

void main() {
  late MockAuthApi api;

  setUp(() => api = MockAuthApi());

  Future<void> pumpLogin(WidgetTester tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authApiProvider.overrideWithValue(api),
          tokenStoreProvider.overrideWithValue(InMemoryTokenStore()),
        ],
        child: const MaterialApp(home: LoginScreen()),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('the submit button reads "Inloggen bij RepairTrack"', (tester) async {
    await pumpLogin(tester);

    expect(
      find.descendant(of: find.byKey(const Key('login-submit')), matching: find.text('Inloggen bij RepairTrack')),
      findsOneWidget,
    );
  });

  testWidgets('validates the fields before calling the backend', (tester) async {
    await pumpLogin(tester);

    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pump();

    expect(find.text('Vul een geldig e-mailadres in'), findsOneWidget);
    expect(find.text('Vul je wachtwoord in'), findsOneWidget);
    verifyNever(() => api.login(email: any(named: 'email'), password: any(named: 'password')));
  });

  testWidgets('shows a clear message for wrong credentials', (tester) async {
    when(() => api.login(email: any(named: 'email'), password: any(named: 'password')))
        .thenThrow(const ApiException(statusCode: 401, code: 'INVALID_CREDENTIALS', message: 'Bad credentials'));
    await pumpLogin(tester);

    await tester.enterText(find.byKey(const Key('login-email')), 'owner@example.nl');
    await tester.enterText(find.byKey(const Key('login-password')), 'wrong-password');
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pumpAndSettle();

    expect(find.text('E-mailadres of wachtwoord is onjuist.'), findsOneWidget);
  });

  testWidgets('an unconfirmed email address offers to resend the confirmation mail', (tester) async {
    when(() => api.login(email: any(named: 'email'), password: any(named: 'password')))
        .thenThrow(const ApiException(statusCode: 403, code: 'EMAIL_NOT_VERIFIED', message: 'not verified'));
    when(() => api.resendVerification(any())).thenAnswer((_) async {});
    await pumpLogin(tester);

    await tester.enterText(find.byKey(const Key('login-email')), 'new@example.nl');
    await tester.enterText(find.byKey(const Key('login-password')), 'correct-password');
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pumpAndSettle();

    expect(find.text('Bevestig eerst je e-mailadres via de link in je mail.'), findsOneWidget);
    await tester.tap(find.byKey(const Key('resend-verification')));
    await tester.pumpAndSettle();

    verify(() => api.resendVerification('new@example.nl')).called(1);
    expect(find.textContaining('nieuwe bevestigingsmail'), findsOneWidget);
  });
}
