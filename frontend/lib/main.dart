import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_web_plugins/url_strategy.dart';

import 'core/auth/idle_sign_out.dart';
import 'core/l10n/l10n.dart';
import 'core/l10n/locale_controller.dart';
import 'core/routing/app_router.dart';
import 'core/theme/app_theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Web: clean URLs (/v/{token}) instead of /#/v/{token}, so share links work as printed.
  usePathUrlStrategy();
  // Read before the first frame, so the app never flashes in the wrong language.
  final language = await _storedLanguage();
  runApp(
    ProviderScope(
      // Failed requests show an error with a retry button instead of being retried silently.
      retry: _noAutomaticRetry,
      overrides: [initialLanguageProvider.overrideWithValue(language)],
      child: const RepairTrackApp(),
    ),
  );
}

Future<AppLanguage?> _storedLanguage() async {
  try {
    return await SharedPreferencesLanguageStore().read();
  } catch (_) {
    // Unreadable storage: fall back to the device language.
    return null;
  }
}

Duration? _noAutomaticRetry(int retryCount, Object error) => null;

class RepairTrackApp extends ConsumerWidget {
  const RepairTrackApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final language = ref.watch(shownLanguageProvider);
    applyFormattingLanguage(language);
    return MaterialApp.router(
      title: 'RepairTrack',
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      routerConfig: ref.watch(routerProvider),
      // Our own texts plus Flutter's (date pickers, dialogs, tooltips) in the same language.
      locale: language.locale,
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      debugShowCheckedModeBanner: false,
      // Signs out after 15 minutes without interaction; the router then shows the login screen.
      builder: (context, child) => IdleSignOut(child: child ?? const SizedBox.shrink()),
    );
  }
}
