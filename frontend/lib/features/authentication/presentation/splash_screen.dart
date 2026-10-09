import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/session_controller.dart';
import '../../../core/widgets/async_value_view.dart';
import '../../../core/widgets/brand_widgets.dart';

/// Shown while the stored session is checked, or when the server is unreachable at start-up.
class SplashScreen extends ConsumerWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionControllerProvider);
    return Scaffold(
      body: switch (session) {
        SessionOffline(:final error) => ErrorView(
            error: error,
            onRetry: () => ref.read(sessionControllerProvider.notifier).restore(),
          ),
        _ => const Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                BrandLogoLarge(width: 180),
                SizedBox(height: 32),
                CircularProgressIndicator(),
              ],
            ),
          ),
      },
    );
  }
}
