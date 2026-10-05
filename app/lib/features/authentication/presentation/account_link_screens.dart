import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/error_messages.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/form_widgets.dart';
import '../data/auth_api.dart';

/// Shown after registering: login works only after the emailed link is followed.
class CheckYourMail extends StatelessWidget {
  const CheckYourMail({super.key, required this.email});

  final String email;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Icon(Icons.mark_email_unread_outlined, size: 56),
        const SizedBox(height: 16),
        Text(
          'We hebben een e-mail gestuurd naar $email. Klik op de link in die mail om je e-mailadres te bevestigen; '
          'daarna kun je inloggen. De link is 24 uur geldig.',
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 24),
        ResendVerificationButton(email: email),
        const SizedBox(height: 8),
        FilledButton(onPressed: () => context.go(Routes.login), child: const Text('Naar inloggen')),
      ],
    );
  }
}

class ResendVerificationButton extends ConsumerStatefulWidget {
  const ResendVerificationButton({super.key, required this.email});

  final String email;

  @override
  ConsumerState<ResendVerificationButton> createState() => _ResendVerificationButtonState();
}

class _ResendVerificationButtonState extends ConsumerState<ResendVerificationButton> {
  bool _busy = false;
  bool _sent = false;

  Future<void> _resend() async {
    setState(() => _busy = true);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(authApiProvider).resendVerification(widget.email);
      if (mounted) {
        setState(() => _sent = true);
      }
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(userMessage(e))));
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_sent) {
      return const InfoBanner(message: 'Er is een nieuwe bevestigingsmail verstuurd. Eerdere links werken niet meer.');
    }
    return OutlinedButton.icon(
      key: const Key('resend-verification'),
      onPressed: _busy ? null : _resend,
      icon: const Icon(Icons.refresh),
      label: const Text('Bevestigingsmail opnieuw versturen'),
    );
  }
}

class ForgotPasswordScreen extends ConsumerStatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  ConsumerState<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends ConsumerState<ForgotPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  bool _busy = false;
  bool _sent = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(authApiProvider).forgotPassword(_email.text.trim());
      if (mounted) {
        setState(() => _sent = true);
      }
    } catch (e) {
      if (mounted) {
        setState(() => _error = userMessage(e));
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Wachtwoord vergeten')),
      body: CenteredForm(
        child: _sent
            ? Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const InfoBanner(
                    message: 'Als dit e-mailadres bij RepairTrack bekend is, ontvang je een mail met een link om een '
                        'nieuw wachtwoord te kiezen. De link is 1 uur geldig.',
                  ),
                  const SizedBox(height: 16),
                  FilledButton(onPressed: () => context.go(Routes.login), child: const Text('Naar inloggen')),
                ],
              )
            : Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    TextFormField(
                      controller: _email,
                      decoration: const InputDecoration(labelText: 'E-mailadres'),
                      keyboardType: TextInputType.emailAddress,
                      onFieldSubmitted: (_) => _submit(),
                      validator: (value) =>
                          (value == null || !value.contains('@')) ? 'Vul een geldig e-mailadres in' : null,
                    ),
                    const SizedBox(height: 16),
                    if (_error != null) ...[
                      ErrorText(message: _error!),
                      const SizedBox(height: 16),
                    ],
                    FilledButton(
                      onPressed: _busy ? null : _submit,
                      child: _busy ? const ButtonProgress() : const Text('Stuur een herstellink'),
                    ),
                    TextButton(onPressed: () => context.go(Routes.login), child: const Text('Terug naar inloggen')),
                  ],
                ),
              ),
      ),
    );
  }
}

/// Opened from the verification email (web app). Confirms the address once and points to the login.
class VerifyEmailScreen extends ConsumerStatefulWidget {
  const VerifyEmailScreen({super.key, required this.token});

  final String token;

  @override
  ConsumerState<VerifyEmailScreen> createState() => _VerifyEmailScreenState();
}

class _VerifyEmailScreenState extends ConsumerState<VerifyEmailScreen> {
  late final Future<void> _verification = ref.read(authApiProvider).verifyEmail(widget.token);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('E-mailadres bevestigen'), automaticallyImplyLeading: false),
      body: CenteredForm(
        child: FutureBuilder<void>(
          future: _verification,
          builder: (context, snapshot) {
            if (snapshot.connectionState != ConnectionState.done) {
              return const Center(child: CircularProgressIndicator());
            }
            final failed = snapshot.hasError;
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Icon(failed ? Icons.link_off : Icons.check_circle_outline, size: 56),
                const SizedBox(height: 16),
                Text(
                  failed
                      ? userMessage(snapshot.error!)
                      : 'Je e-mailadres is bevestigd. Je kunt nu inloggen, ook in de RepairTrack-app.',
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 24),
                FilledButton(onPressed: () => context.go(Routes.login), child: const Text('Naar inloggen')),
              ],
            );
          },
        ),
      ),
    );
  }
}

/// Opened from the password-reset email (web app).
class ResetPasswordScreen extends ConsumerStatefulWidget {
  const ResetPasswordScreen({super.key, required this.token});

  /// Backend policy: at least 12 characters.
  static const minPasswordLength = 12;

  final String token;

  @override
  ConsumerState<ResetPasswordScreen> createState() => _ResetPasswordScreenState();
}

class _ResetPasswordScreenState extends ConsumerState<ResetPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _password = TextEditingController();
  bool _busy = false;
  bool _done = false;
  String? _error;

  @override
  void dispose() {
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(authApiProvider).resetPassword(token: widget.token, newPassword: _password.text);
      if (mounted) {
        setState(() => _done = true);
      }
    } catch (e) {
      if (mounted) {
        setState(() => _error = userMessage(e));
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    const min = ResetPasswordScreen.minPasswordLength;
    return Scaffold(
      appBar: AppBar(title: const Text('Nieuw wachtwoord'), automaticallyImplyLeading: false),
      body: CenteredForm(
        child: _done
            ? Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const InfoBanner(
                    message: 'Je wachtwoord is gewijzigd. Je bent op al je apparaten uitgelogd; log opnieuw in met '
                        'je nieuwe wachtwoord.',
                  ),
                  const SizedBox(height: 16),
                  FilledButton(onPressed: () => context.go(Routes.login), child: const Text('Naar inloggen')),
                ],
              )
            : Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    PasswordFormField(
                      controller: _password,
                      labelText: 'Nieuw wachtwoord',
                      helperText: 'Minimaal $min tekens',
                      autofillHints: const [AutofillHints.newPassword],
                      validator: (value) => (value == null || value.length < min) ? 'Minimaal $min tekens' : null,
                    ),
                    const SizedBox(height: 16),
                    PasswordFormField(
                      labelText: 'Herhaal wachtwoord',
                      onFieldSubmitted: (_) => _submit(),
                      validator: (value) => value != _password.text ? 'De wachtwoorden zijn niet gelijk' : null,
                    ),
                    const SizedBox(height: 16),
                    if (_error != null) ...[
                      ErrorText(message: _error!),
                      const SizedBox(height: 16),
                    ],
                    FilledButton(
                      onPressed: _busy ? null : _submit,
                      child: _busy ? const ButtonProgress() : const Text('Wachtwoord opslaan'),
                    ),
                  ],
                ),
              ),
      ),
    );
  }
}
