import 'package:json_annotation/json_annotation.dart';

import '../format/formatters.dart';

/// Maps a Java `LocalDate` ("2026-09-14") to a [DateTime] at local midnight and back.
class DateOnlyConverter implements JsonConverter<DateTime, String> {
  const DateOnlyConverter();

  @override
  DateTime fromJson(String json) => parseWireDate(json);

  @override
  String toJson(DateTime object) => toWireDate(object);
}

class NullableDateOnlyConverter implements JsonConverter<DateTime?, String?> {
  const NullableDateOnlyConverter();

  @override
  DateTime? fromJson(String? json) => json == null ? null : parseWireDate(json);

  @override
  String? toJson(DateTime? object) => object == null ? null : toWireDate(object);
}
