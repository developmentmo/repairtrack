import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/auth/session_controller.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/brand_widgets.dart';
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
    final l = context.l10n;
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
          _error = userMessage(l, e);
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
    final l = context.l10n;
    return Scaffold(
      body: CenteredForm(
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Center(child: BrandLogoLarge(width: 200)),
              const SizedBox(height: 16),
              Text(
                l.loginTagline,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.titleSmall,
              ),
              const SizedBox(height: 4),
              Text(
                l.loginSubtitle,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: 24),
              if (expired) ...[
                InfoBanner(message: l.errorSessionExpired),
                const SizedBox(height: 16),
              ],
              TextFormField(
                key: const Key('login-email'),
                controller: _email,
                decoration: InputDecoration(labelText: l.commonEmail),
                keyboardType: TextInputType.emailAddress,
                autofillHints: const [AutofillHints.email],
                textInputAction: TextInputAction.next,
                validator: (value) => (value == null || !value.contains('@')) ? l.commonInvalidEmail : null,
              ),
              const SizedBox(height: 16),
              PasswordFormField(
                key: const Key('login-password'),
                controller: _password,
                autofillHints: const [AutofillHints.password],
                onFieldSubmitted: (_) => _submit(),
                validator: (value) => (value == null || value.isEmpty) ? l.loginPasswordRequired : null,
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
                child: _busy ? const ButtonProgress() : Text(l.loginSubmit),
              ),
              const SizedBox(height: 8),
              TextButton(
                onPressed: _busy ? null : () => context.go(Routes.register),
                child: Text(l.loginNoAccount),
              ),
              TextButton(
                key: const Key('login-forgot'),
                onPressed: _busy ? null : () => context.go(Routes.forgotPassword),
                child: Text(l.loginForgotPassword),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
