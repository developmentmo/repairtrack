import 'package:json_annotation/json_annotation.dart';

part 'user.g.dart';

/// `GET /api/v1/users/me`
@JsonSerializable(createToJson: false)
class User {
  const User({
    required this.id,
    required this.email,
    required this.firstName,
    required this.lastName,
    required this.roles,
  });

  factory User.fromJson(Map<String, dynamic> json) => _$UserFromJson(json);

  final String id;
  final String email;
  final String firstName;
  final String lastName;

  /// Platform roles (`OWNER`, `SYSTEM_ADMIN`). Garage roles come from the garage memberships.
  final List<String> roles;

  bool get isSystemAdmin => roles.contains('SYSTEM_ADMIN');
}
