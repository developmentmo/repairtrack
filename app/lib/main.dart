import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/routing/app_router.dart';
import 'core/theme/app_theme.dart';

void main() {
  runApp(
    const ProviderScope(
      // Failed requests show an error with a retry button instead of being retried silently.
      retry: _noAutomaticRetry,
      child: RepairTrackApp(),
    ),
  );
}

Duration? _noAutomaticRetry(int retryCount, Object error) => null;

class RepairTrackApp extends ConsumerWidget {
  const RepairTrackApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return MaterialApp.router(
      title: 'RepairTrack',
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      routerConfig: ref.watch(routerProvider),
      debugShowCheckedModeBanner: false,
    );
  }
}
