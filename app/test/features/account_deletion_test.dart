import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:repairtrack_app/core/network/api_exception.dart';
import 'package:repairtrack_app/core/network/dio_provider.dart';
import 'package:repairtrack_app/features/authentication/data/auth_api.dart';
import 'package:repairtrack_app/features/authentication/presentation/account_screen.dart';

import '../helpers/in_memory_token_store.dart';

class MockAuthApi extends Mock implements AuthApi {}

void main() {
  late MockAuthApi api;

  setUp(() => api = MockAuthApi());

  Future<void> openDialog(WidgetTester tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authApiProvider.overrideWithValue(api),
          tokenStoreProvider.overrideWithValue(InMemoryTokenStore()),
        ],
        child: const MaterialApp(home: AccountScreen()),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('delete-account')));
    await tester.pumpAndSettle();
  }

  testWidgets('asks for the password and shows a wrong password', (tester) async {
    when(() => api.deleteAccount(any())).thenThrow(
      const ApiException(statusCode: 403, code: 'PASSWORD_INCORRECT', message: 'wrong'),
    );
    await openDialog(tester);

    await tester.tap(find.byKey(const Key('delete-account-confirm')));
    await tester.pump();
    expect(find.text('Vul je wachtwoord in.'), findsOneWidget);
    verifyNever(() => api.deleteAccount(any()));

    await tester.enterText(find.byKey(const Key('delete-account-password')), 'guess');
    await tester.tap(find.byKey(const Key('delete-account-confirm')));
    await tester.pumpAndSettle();
    expect(find.text('Het wachtwoord klopt niet.'), findsOneWidget);
  });

  testWidgets('deletes the account and closes the dialog', (tester) async {
    when(() => api.deleteAccount(any())).thenAnswer((_) async {});
    await openDialog(tester);

    await tester.enterText(find.byKey(const Key('delete-account-password')), 'correct password');
    await tester.tap(find.byKey(const Key('delete-account-confirm')));
    await tester.pumpAndSettle();

    verify(() => api.deleteAccount('correct password')).called(1);
    expect(find.text('Je account is verwijderd.'), findsOneWidget);
    expect(find.byKey(const Key('delete-account-confirm')), findsNothing);
  });
}
