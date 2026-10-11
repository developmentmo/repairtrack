import '../l10n/l10n.dart';
import 'api_exception.dart';

/// User-facing text for an error, in the shown language. Branches on the backend's stable error codes.
String userMessage(AppLocalizations l, Object error) {
  if (error is! ApiException) {
    return l.errorGeneric;
  }
  return switch (error.code) {
    ApiException.network => l.errorNetwork,
    'INVALID_CREDENTIALS' => l.errorInvalidCredentials,
    'PASSWORD_INCORRECT' => l.errorPasswordIncorrect,
    'LAST_GARAGE_ADMIN' =>
      l.errorLastGarageAdmin,
    'ACCOUNT_BLOCKED' => l.errorAccountBlocked,
    'EMAIL_NOT_VERIFIED' => l.errorEmailNotVerified,
    'INVALID_TOKEN' => l.errorInvalidToken,
    'SYSTEM_ADMIN_REQUIRED' => l.errorSystemAdminRequired,
    'CANNOT_BLOCK_YOURSELF' => l.errorCannotBlockYourself,
    'USER_NOT_FOUND' => l.errorUserNotFound,
    'TOO_MANY_LOGIN_ATTEMPTS' => l.errorTooManyLoginAttempts,
    'RATE_LIMITED' => l.errorRateLimited,
    'EMAIL_ALREADY_REGISTERED' => l.errorEmailAlreadyRegistered,
    'INVALID_PASSWORD' => l.errorInvalidPassword,
    'UNAUTHORIZED' || 'INVALID_REFRESH_TOKEN' || 'SESSION_EXPIRED' => l.errorSessionExpired,
    'VEHICLE_ALREADY_REGISTERED' =>
      l.errorVehicleAlreadyRegistered,
    'VEHICLE_ALREADY_OWNED' => l.errorVehicleAlreadyOwned,
    'ALREADY_VEHICLE_OWNER' => l.errorAlreadyVehicleOwner,
    'OWNERSHIP_PROOF_INVALID' => l.errorOwnershipProofInvalid,
    'INVALID_OWNERSHIP_PERIOD' => l.errorInvalidOwnershipPeriod,
    'INVALID_VEHICLE_SEARCH' => l.errorInvalidVehicleSearch,
    'VEHICLE_NOT_FOUND' => l.errorVehicleNotFound,
    'VEHICLE_UNDER_DISPUTE' =>
      l.errorVehicleUnderDispute,
    'VEHICLE_NOT_OWNED' => l.errorVehicleNotOwned,
    'DISPUTE_NOT_FOUND' => l.errorDisputeNotFound,
    'DISPUTE_ALREADY_OPEN' => l.errorDisputeAlreadyOpen,
    'TOO_MANY_OPEN_DISPUTES' => l.errorTooManyOpenDisputes,
    'TOO_MUCH_EVIDENCE' => l.errorTooMuchEvidence,
    'DISPUTE_CLOSED' => l.errorDisputeClosed,
    'DISPUTE_NOT_REVIEWABLE' => l.errorDisputeNotReviewable,
    'DISPUTE_ACCESS_DENIED' => l.errorDisputeAccessDenied,
    'OWNERSHIP_CHANGED' => l.errorOwnershipChanged,
    'INVALID_DISPUTE_DATA' => l.errorInvalidInput(error.message),
    'REGISTRY_VEHICLE_NOT_FOUND' => l.errorRegistryVehicleNotFound,
    'REGISTRY_UNAVAILABLE' => l.errorRegistryUnavailable,
    'REPAIR_NOT_FOUND' => l.errorRepairNotFound,
    'FORBIDDEN' || 'VEHICLE_ACCESS_DENIED' || 'REPAIR_ACCESS_DENIED' => l.errorAccessDenied,
    'GARAGE_ACCESS_DENIED' => l.errorGarageAccessDenied,
    'GARAGE_SUSPENDED' => l.errorGarageSuspended,
    'GARAGE_NOT_FOUND' => l.errorGarageNotFound,
    'INVALID_VERIFICATION_TRANSITION' => l.errorInvalidVerificationTransition,
    'NO_CHANGES' => l.errorNoChanges,
    'REPAIR_ALREADY_VOIDED' => l.errorRepairAlreadyVoided,
    'EMPTY_FILE' => l.errorEmptyFile,
    'FILE_TOO_LARGE' => l.errorFileTooLarge,
    'MALWARE_DETECTED' => l.errorMalwareDetected,
    'SCANNER_UNAVAILABLE' => l.errorScannerUnavailable,
    'UNSUPPORTED_FILE_TYPE' => l.errorUnsupportedFileType,
    'DOCUMENT_NOT_FOUND' => l.errorDocumentNotFound,
    'VALIDATION_FAILED' || 'INVALID_VEHICLE_DATA' || 'INVALID_REPAIR_DATA' || 'INVALID_GARAGE_DATA' =>
      l.errorInvalidInput(error.message),
    _ => l.errorWithCode(error.code),
  };
}

/// User-facing text for an error while viewing or uploading a vehicle photo.
String vehiclePhotoMessage(AppLocalizations l, Object error) {
  if (error is! ApiException) {
    return userMessage(l, error);
  }
  return switch (error.code) {
    'VEHICLE_ACCESS_DENIED' => l.photoErrorVehicleAccessDenied,
    'VEHICLE_NOT_FOUND' => l.photoErrorVehicleNotFound,
    'EMPTY_FILE' => l.photoErrorEmptyFile,
    'FILE_TOO_LARGE' => l.photoErrorFileTooLarge,
    'UNSUPPORTED_FILE_TYPE' => l.photoErrorUnsupportedFileType,
    'MALWARE_DETECTED' => l.photoErrorMalwareDetected,
    'SCANNER_UNAVAILABLE' => l.photoErrorScannerUnavailable,
    'VEHICLE_PHOTO_CONFLICT' => l.photoErrorVehiclePhotoConflict,
    _ => userMessage(l, error),
  };
}
