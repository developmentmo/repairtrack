/// Formatting helpers for display (Dutch conventions) and for the API wire format.
library;

String _two(int value) => value.toString().padLeft(2, '0');

/// 14-09-2026
String formatDate(DateTime date) => '${_two(date.day)}-${_two(date.month)}-${date.year}';

/// 14-09-2026 10:30 in the device's time zone (for instants from the API).
String formatDateTime(DateTime instant) {
  final local = instant.toLocal();
  return '${formatDate(local)} ${_two(local.hour)}:${_two(local.minute)}';
}

/// 183.421 km
String formatKm(int km) => '${formatThousands(km)} km';

String formatThousands(int value) {
  final digits = value.abs().toString();
  final buffer = StringBuffer(value < 0 ? '-' : '');
  for (var i = 0; i < digits.length; i++) {
    if (i > 0 && (digits.length - i) % 3 == 0) {
      buffer.write('.');
    }
    buffer.write(digits[i]);
  }
  return buffer.toString();
}

/// API format for calendar dates (Java LocalDate): 2026-09-14
String toWireDate(DateTime date) => '${date.year.toString().padLeft(4, '0')}-${_two(date.month)}-${_two(date.day)}';

/// Parses an API calendar date as a local date without time-zone shifts.
DateTime parseWireDate(String value) {
  final parts = value.split('-');
  return DateTime(int.parse(parts[0]), int.parse(parts[1]), int.parse(parts[2]));
}

DateTime today() {
  final now = DateTime.now();
  return DateTime(now.year, now.month, now.day);
}
