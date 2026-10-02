import 'api_exception.dart';

/// User-facing (Dutch) text for an error. Branches on the backend's stable error codes.
String userMessage(Object error) {
  if (error is! ApiException) {
    return 'Er ging iets mis. Probeer het opnieuw.';
  }
  return switch (error.code) {
    ApiException.network => 'Geen verbinding met de server. Controleer je verbinding en probeer het opnieuw.',
    'INVALID_CREDENTIALS' => 'E-mailadres of wachtwoord is onjuist.',
    'ACCOUNT_BLOCKED' => 'Dit account is geblokkeerd.',
    'EMAIL_ALREADY_REGISTERED' => 'Er bestaat al een account met dit e-mailadres.',
    'INVALID_PASSWORD' => 'Het wachtwoord moet minimaal 12 tekens hebben.',
    'UNAUTHORIZED' || 'INVALID_REFRESH_TOKEN' => 'Je sessie is verlopen. Log opnieuw in.',
    'VEHICLE_ALREADY_REGISTERED' =>
      'Dit voertuig is al geregistreerd. Zoek het op kenteken en claim het met het VIN.',
    'VEHICLE_ALREADY_OWNED' => 'Dit voertuig heeft al een eigenaar.',
    'ALREADY_VEHICLE_OWNER' => 'Je bent al eigenaar van dit voertuig.',
    'OWNERSHIP_PROOF_INVALID' => 'Het VIN komt niet overeen met dit voertuig.',
    'INVALID_OWNERSHIP_PERIOD' => 'Deze datum overlapt met de vorige eigenaar of ligt in de toekomst.',
    'INVALID_VEHICLE_SEARCH' => 'Vul een kenteken in.',
    'VEHICLE_NOT_FOUND' => 'Voertuig niet gevonden.',
    'REPAIR_NOT_FOUND' => 'Registratie niet gevonden.',
    'FORBIDDEN' || 'VEHICLE_ACCESS_DENIED' || 'REPAIR_ACCESS_DENIED' => 'Je hebt geen toegang tot deze gegevens.',
    'VALIDATION_FAILED' || 'INVALID_VEHICLE_DATA' || 'INVALID_REPAIR_DATA' => 'Controleer de invoer: ${error.message}',
    _ => 'Er ging iets mis (${error.code}).',
  };
}
