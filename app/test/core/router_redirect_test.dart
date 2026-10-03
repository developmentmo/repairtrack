import 'package:flutter_test/flutter_test.dart';
import 'package:repairtrack_app/core/auth/session_controller.dart';
import 'package:repairtrack_app/core/routing/app_router.dart';
import 'package:repairtrack_app/core/routing/routes.dart';
import 'package:repairtrack_app/features/authentication/domain/user.dart';

void main() {
  const user = User(id: 'u1', email: 'a@b.nl', firstName: 'An', lastName: 'Tester', roles: ['OWNER']);

  test('while the session is restored everything waits on the splash screen', () {
    expect(redirectFor(const SessionRestoring(), Routes.vehicle('v1')), Routes.splash);
    expect(redirectFor(const SessionRestoring(), Routes.splash), isNull);
  });

  test('signed out users can only see login and register', () {
    expect(redirectFor(const SignedOut(), Routes.home), Routes.login);
    expect(redirectFor(const SignedOut(), Routes.login), isNull);
    expect(redirectFor(const SignedOut(), Routes.register), isNull);
  });

  test('signed in users skip the auth pages', () {
    expect(redirectFor(const SignedIn(user), Routes.login), Routes.home);
    expect(redirectFor(const SignedIn(user), Routes.splash), Routes.home);
    expect(redirectFor(const SignedIn(user), Routes.vehicle('v1')), isNull);
  });

  test('the public report is reachable in every session state', () {
    final link = Routes.publicReport('abc');
    expect(redirectFor(const SessionRestoring(), link), isNull);
    expect(redirectFor(const SignedOut(), link), isNull);
    expect(redirectFor(const SignedIn(user), link), isNull);
  });
}
