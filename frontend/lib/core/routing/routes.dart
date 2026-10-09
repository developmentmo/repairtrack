/// Route paths. Screens navigate with these helpers instead of string literals.
class Routes {
  const Routes._();

  static const splash = '/splash';
  static const login = '/login';
  static const register = '/register';
  static const home = '/';
  static const forgotPassword = '/forgot-password';

  // Opened from email links ({PUBLIC_BASE_URL}/verify-email?token=..., /reset-password?token=...); web app.
  static const verifyEmail = '/verify-email';
  static const resetPassword = '/reset-password';

  // SYSTEM_ADMIN
  static const admin = '/admin';

  // The signed-in user's account (also the "delete your account" page the app stores link to)
  static const account = '/account';

  // Owner
  static const addVehicle = '/vehicles/add';

  // Ownership disputes (emails link to [disputes])
  static const disputes = '/disputes';

  static String openDispute(String vehicleId) => '/disputes/new/$vehicleId';

  static String vehicle(String vehicleId) => '/vehicles/$vehicleId';

  static String vehicleHistory(String vehicleId) => '/vehicles/$vehicleId/history';

  static String newRepair(String vehicleId) => '/vehicles/$vehicleId/repairs/new';

  static String shareVehicle(String vehicleId) => '/vehicles/$vehicleId/share';

  // Garage: the same screens, opened on behalf of a garage
  static const newGarage = '/garages/new';

  static String garage(String garageId) => '/garages/$garageId';

  static String garageAddVehicle(String garageId) => '/garages/$garageId/vehicles/add';

  static String garageVehicle(String garageId, String vehicleId) => '/garages/$garageId/vehicles/$vehicleId';

  static String garageVehicleHistory(String garageId, String vehicleId) =>
      '/garages/$garageId/vehicles/$vehicleId/history';

  static String garageNewRepair(String garageId, String vehicleId) =>
      '/garages/$garageId/vehicles/$vehicleId/repairs/new';

  // Public: the shared report behind a share link ({PUBLIC_BASE_URL}/v/{token}). No login.
  static const publicPrefix = '/v/';

  static String publicReport(String token) => '$publicPrefix$token';

  // Shared
  static String repair(String repairId) => '/repairs/$repairId';

  static String correctRepair(String repairId) => '/repairs/$repairId/correct';

  /// Vehicle screens for the owner (no garage) or on behalf of a garage.
  static String vehicleFor(String? garageId, String vehicleId) =>
      garageId == null ? vehicle(vehicleId) : garageVehicle(garageId, vehicleId);

  static String vehicleHistoryFor(String? garageId, String vehicleId) =>
      garageId == null ? vehicleHistory(vehicleId) : garageVehicleHistory(garageId, vehicleId);

  static String newRepairFor(String? garageId, String vehicleId) =>
      garageId == null ? newRepair(vehicleId) : garageNewRepair(garageId, vehicleId);
}
