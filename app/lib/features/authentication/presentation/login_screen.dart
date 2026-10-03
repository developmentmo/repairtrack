import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/auth/session_controller.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/form_widgets.dart';
import 'account_link_screens.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _busy = false;
  String? _error;
  bool _unverified = false;

  @override
  void dispose() {
    _email.dispose();
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
      _unverified = false;
    });
    try {
      // On success the router redirects to the dashboard.
      await ref.read(sessionControllerProvider.notifier).login(email: _email.text.trim(), password: _password.text);
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = userMessage(e);
          _unverified = e is ApiException && e.code == 'EMAIL_NOT_VERIFIED';
        });
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(sessionControllerProvider);
    final expired = session is SignedOut && session.expired;
    return Scaffold(
      body: CenteredForm(
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('RepairTrack', style: Theme.of(context).textTheme.headlineMedium, textAlign: TextAlign.center),
              const SizedBox(height: 8),
              const Text('Log in om je voertuighistorie te bekijken.', textAlign: TextAlign.center),
              const SizedBox(height: 24),
              if (expired) ...[
                const InfoBanner(message: 'Je sessie is verlopen. Log opnieuw in.'),
                const SizedBox(height: 16),
              ],
              TextFormField(
                key: const Key('login-email'),
                controller: _email,
                decoration: const InputDecoration(labelText: 'E-mailadres'),
                keyboardType: TextInputType.emailAddress,
                autofillHints: const [AutofillHints.email],
                textInputAction: TextInputAction.next,
                validator: (value) => (value == null || !value.contains('@')) ? 'Vul een geldig e-mailadres in' : null,
              ),
              const SizedBox(height: 16),
              TextFormField(
                key: const Key('login-password'),
                controller: _password,
                decoration: const InputDecoration(labelText: 'Wachtwoord'),
                obscureText: true,
                autofillHints: const [AutofillHints.password],
                onFieldSubmitted: (_) => _submit(),
                validator: (value) => (value == null || value.isEmpty) ? 'Vul je wachtwoord in' : null,
              ),
              const SizedBox(height: 16),
              if (_error != null) ...[
                ErrorText(message: _error!),
                const SizedBox(height: 16),
              ],
              if (_unverified) ...[
                ResendVerificationButton(email: _email.text.trim()),
                const SizedBox(height: 16),
              ],
              FilledButton(
                key: const Key('login-submit'),
                onPressed: _busy ? null : _submit,
                child: _busy ? const ButtonProgress() : const Text('Inloggen'),
              ),
              const SizedBox(height: 8),
              TextButton(
                onPressed: _busy ? null : () => context.go(Routes.register),
                child: const Text('Nog geen account? Registreren'),
              ),
              TextButton(
                key: const Key('login-forgot'),
                onPressed: _busy ? null : () => context.go(Routes.forgotPassword),
                child: const Text('Wachtwoord vergeten?'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
