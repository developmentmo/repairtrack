import 'package:flutter_test/flutter_test.dart';
import 'package:repairtrack_app/features/admin/domain/admin_user.dart';

void main() {
  test('parses an admin view of a user', () {
    final user = AdminUser.fromJson({
      'id': 'u1',
      'email': 'ulla@example.nl',
      'firstName': 'Ulla',
      'lastName': 'Tester',
      'status': 'BLOCKED',
      'roles': ['OWNER'],
      'emailVerified': true,
      'createdAt': '2026-10-01T10:00:00Z',
    });

    expect(user.isBlocked, isTrue);
    expect(user.emailVerified, isTrue);
  });
}
