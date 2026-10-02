/// Route paths. Screens navigate with these helpers instead of string literals.
class Routes {
  const Routes._();

  static const splash = '/splash';
  static const login = '/login';
  static const register = '/register';
  static const home = '/';
  static const addVehicle = '/vehicles/add';

  static String vehicle(String vehicleId) => '/vehicles/$vehicleId';

  static String vehicleHistory(String vehicleId) => '/vehicles/$vehicleId/history';

  static String newRepair(String vehicleId) => '/vehicles/$vehicleId/repairs/new';

  static String repair(String repairId) => '/repairs/$repairId';
}
