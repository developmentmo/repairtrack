import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/app_config.dart';
import 'session_controller.dart';

/// Signs the user out after [timeout] without taps, scrolls or key presses, and keeps the server-side session alive
/// while the user is active.
///
/// The backend ends a session after the same idle time without requests. Interaction that causes no requests (reading,
/// filling in a form) still counts here, so it is reported to the server at most every [keepAliveInterval].
/// Elapsed time is measured with the wall clock, so time spent in the background or with the device asleep counts too.
class IdleSignOut extends ConsumerStatefulWidget {
  const IdleSignOut({
    super.key,
    required this.child,
    this.timeout = AppConfig.sessionIdleTimeout,
    this.keepAliveInterval = const Duration(minutes: 5),
    this.checkInterval = const Duration(seconds: 15),
    this.now = DateTime.now,
  });

  final Widget child;
  final Duration timeout;
  final Duration keepAliveInterval;
  final Duration checkInterval;

  /// The clock; replaced in tests.
  final DateTime Function() now;

  @override
  ConsumerState<IdleSignOut> createState() => _IdleSignOutState();
}

class _IdleSignOutState extends ConsumerState<IdleSignOut> {
  late DateTime _lastActivity;
  late DateTime _lastKeepAlive;
  Timer? _timer;
  AppLifecycleListener? _lifecycle;

  @override
  void initState() {
    super.initState();
    _lastActivity = _lastKeepAlive = widget.now();
    _timer = Timer.periodic(widget.checkInterval, (_) => _check());
    // Timers do not run while a phone app is suspended: check as soon as it comes back.
    _lifecycle = AppLifecycleListener(onResume: _check);
    HardwareKeyboard.instance.addHandler(_onKey);
  }

  @override
  void dispose() {
    _timer?.cancel();
    _lifecycle?.dispose();
    HardwareKeyboard.instance.removeHandler(_onKey);
    super.dispose();
  }

  bool _onKey(KeyEvent event) {
    _recordActivity();
    return false; // never consume the key
  }

  void _recordActivity() {
    final now = widget.now();
    if (_isIdle(now)) {
      // Too late: the first tap after the timeout must not revive the session.
      _check();
      return;
    }
    _lastActivity = now;
    if (now.difference(_lastKeepAlive) >= widget.keepAliveInterval) {
      _lastKeepAlive = now;
      unawaited(ref.read(sessionControllerProvider.notifier).keepAlive());
    }
  }

  bool _isIdle(DateTime now) => now.difference(_lastActivity) >= widget.timeout;

  void _check() {
    if (ref.read(sessionControllerProvider) is SignedIn && _isIdle(widget.now())) {
      unawaited(ref.read(sessionControllerProvider.notifier).idleTimeout());
    }
  }

  @override
  Widget build(BuildContext context) {
    // A new login starts with a full idle period.
    ref.listen<SessionState>(sessionControllerProvider, (previous, next) {
      if (next is SignedIn && previous is! SignedIn) {
        _lastActivity = _lastKeepAlive = widget.now();
      }
    });
    return Listener(
      behavior: HitTestBehavior.translucent,
      onPointerDown: (_) => _recordActivity(),
      onPointerSignal: (_) => _recordActivity(),
      child: widget.child,
    );
  }
}
