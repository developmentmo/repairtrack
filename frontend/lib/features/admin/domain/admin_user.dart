import 'package:json_annotation/json_annotation.dart';

part 'admin_user.g.dart';

/// `GET /admin/users?email=`: an account as seen by a system admin. No credentials.
@JsonSerializable(createToJson: false)
class AdminUser {
  const AdminUser({
    required this.id,
    required this.email,
    required this.firstName,
    required this.lastName,
    required this.status,
    required this.roles,
    required this.emailVerified,
    required this.createdAt,
  });

  factory AdminUser.fromJson(Map<String, dynamic> json) => _$AdminUserFromJson(json);

  final String id;
  final String email;
  final String firstName;
  final String lastName;

  /// `ACTIVE`, `BLOCKED`, `DELETED`.
  final String status;
  final List<String> roles;
  final bool emailVerified;
  final DateTime createdAt;

  bool get isBlocked => status == 'BLOCKED';
}
