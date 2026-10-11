import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_web_plugins/url_strategy.dart';

import 'core/auth/idle_sign_out.dart';
import 'core/routing/app_router.dart';
import 'core/theme/app_theme.dart';

void main() {
  // Web: clean URLs (/v/{token}) instead of /#/v/{token}, so share links work as printed.
  usePathUrlStrategy();
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
      // Dutch everywhere: our own texts are Dutch, and these make date pickers, dialogs and tooltips Dutch too.
      locale: const Locale('nl', 'NL'),
      supportedLocales: const [Locale('nl', 'NL')],
      localizationsDelegates: GlobalMaterialLocalizations.delegates,
      debugShowCheckedModeBanner: false,
      // Signs out after 15 minutes without interaction; the router then shows the login screen.
      builder: (context, child) => IdleSignOut(child: child ?? const SizedBox.shrink()),
    );
  }
}
