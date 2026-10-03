import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/authentication/presentation/login_screen.dart';
import '../../features/authentication/presentation/register_screen.dart';
import '../../features/authentication/presentation/splash_screen.dart';
import '../../features/garages/presentation/create_garage_screen.dart';
import '../../features/garages/presentation/garage_dashboard_screen.dart';
import '../../features/repairs/presentation/correct_repair_screen.dart';
import '../../features/repairs/presentation/create_repair_screen.dart';
import '../../features/repairs/presentation/repair_detail_screen.dart';
import '../../features/repairs/presentation/vehicle_history_screen.dart';
import '../../features/sharing/presentation/public_report_screen.dart';
import '../../features/sharing/presentation/share_vehicle_screen.dart';
import '../../features/vehicles/presentation/add_vehicle_screen.dart';
import '../../features/vehicles/presentation/owner_dashboard_screen.dart';
import '../../features/vehicles/presentation/vehicle_detail_screen.dart';
import '../auth/session_controller.dart';
import 'routes.dart';

final routerProvider = Provider<GoRouter>((ref) {
  // GoRouter re-runs `redirect` whenever the session changes.
  final sessionChanges = ValueNotifier<SessionState>(ref.read(sessionControllerProvider));
  ref.listen<SessionState>(sessionControllerProvider, (_, next) => sessionChanges.value = next);

  final router = GoRouter(
    initialLocation: Routes.home,
    refreshListenable: sessionChanges,
    redirect: (context, state) => redirectFor(ref.read(sessionControllerProvider), state.matchedLocation),
    routes: [
      GoRoute(path: Routes.splash, builder: (context, state) => const SplashScreen()),
      GoRoute(path: Routes.login, builder: (context, state) => const LoginScreen()),
      GoRoute(path: Routes.register, builder: (context, state) => const RegisterScreen()),
      GoRoute(
        path: '/v/:token',
        builder: (context, state) => PublicReportScreen(token: state.pathParameters['token']!),
      ),
      GoRoute(
        path: Routes.home,
        builder: (context, state) => const OwnerDashboardScreen(),
        routes: [
          GoRoute(path: 'vehicles/add', builder: (context, state) => const AddVehicleScreen()),
          GoRoute(
            path: 'vehicles/:vehicleId',
            builder: (context, state) => VehicleDetailScreen(vehicleId: state.pathParameters['vehicleId']!),
            routes: [
              GoRoute(
                path: 'history',
                builder: (context, state) => VehicleHistoryScreen(vehicleId: state.pathParameters['vehicleId']!),
              ),
              GoRoute(
                path: 'repairs/new',
                builder: (context, state) => CreateRepairScreen(vehicleId: state.pathParameters['vehicleId']!),
              ),
              GoRoute(
                path: 'share',
                builder: (context, state) => ShareVehicleScreen(vehicleId: state.pathParameters['vehicleId']!),
              ),
            ],
          ),
          GoRoute(path: 'garages/new', builder: (context, state) => const CreateGarageScreen()),
          GoRoute(
            path: 'garages/:garageId',
            builder: (context, state) => GarageDashboardScreen(garageId: state.pathParameters['garageId']!),
            routes: [
              GoRoute(
                path: 'vehicles/add',
                builder: (context, state) => AddVehicleScreen(garageId: state.pathParameters['garageId']),
              ),
              GoRoute(
                path: 'vehicles/:vehicleId',
                builder: (context, state) => VehicleDetailScreen(
                  vehicleId: state.pathParameters['vehicleId']!,
                  garageId: state.pathParameters['garageId'],
                ),
                routes: [
                  GoRoute(
                    path: 'history',
                    builder: (context, state) => VehicleHistoryScreen(
                      vehicleId: state.pathParameters['vehicleId']!,
                      garageId: state.pathParameters['garageId'],
                    ),
                  ),
                  GoRoute(
                    path: 'repairs/new',
                    builder: (context, state) => CreateRepairScreen(
                      vehicleId: state.pathParameters['vehicleId']!,
                      garageId: state.pathParameters['garageId'],
                    ),
                  ),
                ],
              ),
            ],
          ),
          GoRoute(
            path: 'repairs/:repairId',
            builder: (context, state) => RepairDetailScreen(repairId: state.pathParameters['repairId']!),
            routes: [
              GoRoute(
                path: 'correct',
                builder: (context, state) => CorrectRepairScreen(repairId: state.pathParameters['repairId']!),
              ),
            ],
          ),
        ],
      ),
    ],
  );

  ref.onDispose(() {
    router.dispose();
    sessionChanges.dispose();
  });
  return router;
});

/// Pure redirect rules, unit-tested separately.
@visibleForTesting
String? redirectFor(SessionState session, String location) {
  // The public report is for anyone with the link, logged in or not, and never waits for the session.
  if (location.startsWith(Routes.publicPrefix)) {
    return null;
  }
  final onAuthPage = location == Routes.login || location == Routes.register;
  return switch (session) {
    SessionRestoring() || SessionOffline() => location == Routes.splash ? null : Routes.splash,
    SignedOut() => onAuthPage ? null : Routes.login,
    SignedIn() => (onAuthPage || location == Routes.splash) ? Routes.home : null,
  };
}
