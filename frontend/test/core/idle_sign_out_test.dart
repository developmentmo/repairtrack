import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:repairtrack_app/core/auth/auth_tokens.dart';
import 'package:repairtrack_app/core/auth/idle_sign_out.dart';
import 'package:repairtrack_app/core/auth/session_controller.dart';
import 'package:repairtrack_app/core/network/dio_provider.dart';
import 'package:repairtrack_app/features/authentication/data/auth_api.dart';
import 'package:repairtrack_app/features/authentication/domain/user.dart';

import '../helpers/in_memory_token_store.dart';

const _user = User(id: 'u1', email: 'a@b.nl', firstName: 'An', lastName: 'Tester', roles: ['OWNER']);

class FakeSessionController extends SessionController {
  FakeSessionController(this.initial);

  final SessionState initial;
  int idleTimeouts = 0;
  int keepAlives = 0;

  @override
  SessionState build() => initial;

  @override
  Future<void> idleTimeout() async {
    idleTimeouts++;
    state = const SignedOut(expired: true);
  }

  @override
  Future<void> keepAlive() async => keepAlives++;
}

class MockAuthApi extends Mock implements AuthApi {}

void main() {
  group('IdleSignOut', () {
    late DateTime now;
    late FakeSessionController session;

    Future<void> pump(WidgetTester tester, {SessionState initial = const SignedIn(_user)}) async {
      now = DateTime(2026, 10, 10, 10);
      session = FakeSessionController(initial);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [sessionControllerProvider.overrideWith(() => session)],
          child: MaterialApp(
            home: IdleSignOut(
              now: () => now,
              child: const Scaffold(body: Center(child: Text('Dashboard'))),
            ),
          ),
        ),
      );
    }

    Future<void> wait(WidgetTester tester, Duration duration) async {
      now = now.add(duration);
      await tester.pump(const Duration(seconds: 15)); // one check
    }

    testWidgets('signs out after 15 minutes without interaction', (tester) async {
      await pump(tester);

      await wait(tester, const Duration(minutes: 14));
      expect(session.idleTimeouts, 0);

      await wait(tester, const Duration(minutes: 1));
      expect(session.idleTimeouts, 1);
      expect(session.state, isA<SignedOut>().having((s) => s.expired, 'expired', isTrue));
    });

    testWidgets('a tap starts the idle period again and keeps the server session alive', (tester) async {
      await pump(tester);

      await wait(tester, const Duration(minutes: 10));
      await tester.tap(find.text('Dashboard'));
      await wait(tester, const Duration(minutes: 10));

      expect(session.idleTimeouts, 0);
      expect(session.keepAlives, 1);
    });

    testWidgets('the server is told about activity at most every 5 minutes', (tester) async {
      await pump(tester);

      await wait(tester, const Duration(minutes: 1));
      await tester.tap(find.text('Dashboard'));
      await wait(tester, const Duration(minutes: 1));
      await tester.tap(find.text('Dashboard'));

      expect(session.keepAlives, 0);
    });

    testWidgets('a tap after the timeout does not revive the session', (tester) async {
      await pump(tester);

      now = now.add(const Duration(minutes: 16)); // e.g. the app was in the background
      await tester.tap(find.text('Dashboard'));
      await tester.pump();

      expect(session.idleTimeouts, 1);
      expect(session.keepAlives, 0);
    });

    testWidgets('does nothing while signed out', (tester) async {
      await pump(tester, initial: const SignedOut());

      await wait(tester, const Duration(hours: 1));

      expect(session.idleTimeouts, 0);
    });
  });

  group('SessionController.idleTimeout', () {
    test('forgets the tokens, ends the session on the server and reports it as expired', () async {
      final api = MockAuthApi();
      when(() => api.logout(any())).thenAnswer((_) async {});
      when(() => api.me()).thenAnswer((_) async => _user);
      final store = InMemoryTokenStore(const AuthTokens(accessToken: 'access', refreshToken: 'refresh'));
      final container = ProviderContainer(
        overrides: [authApiProvider.overrideWithValue(api), tokenStoreProvider.overrideWithValue(store)],
      );
      addTearDown(container.dispose);
      final controller = container.read(sessionControllerProvider.notifier);
      await controller.restore();
      expect(container.read(sessionControllerProvider), isA<SignedIn>());

      await controller.idleTimeout();

      expect(container.read(sessionControllerProvider), isA<SignedOut>().having((s) => s.expired, 'expired', isTrue));
      expect(store.tokens, isNull);
      verify(() => api.logout('refresh')).called(1);
    });
  });
}
