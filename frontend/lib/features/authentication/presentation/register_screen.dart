import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/auth/session_controller.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/form_widgets.dart';
import 'account_link_screens.dart';

class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  /// Backend policy: at least 12 characters (max 72 bytes, checked by the backend).
  static const minPasswordLength = 12;

  final _formKey = GlobalKey<FormState>();
  final _firstName = TextEditingController();
  final _lastName = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _busy = false;
  String? _error;

  /// Set after a successful registration: the user must confirm the address before logging in.
  String? _registeredEmail;

  @override
  void dispose() {
    for (final controller in [_firstName, _lastName, _email, _password]) {
      controller.dispose();
    }
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
    });
    try {
      await ref.read(sessionControllerProvider.notifier).register(
            email: _email.text.trim(),
            password: _password.text,
            firstName: _firstName.text.trim(),
            lastName: _lastName.text.trim(),
          );
      if (mounted) {
        setState(() => _registeredEmail = _email.text.trim());
      }
    } catch (e) {
      if (mounted) {
        setState(() => _error = userMessage(l, e));
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  String? _required(String? value) => (value == null || value.trim().isEmpty) ? context.l10n.commonRequiredField : null;

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final registered = _registeredEmail;
    if (registered != null) {
      return Scaffold(
        appBar: AppBar(title: Text(l.registerConfirmEmailTitle)),
        body: CenteredForm(child: CheckYourMail(email: registered)),
      );
    }
    return Scaffold(
      appBar: AppBar(title: Text(l.registerTitle)),
      body: CenteredForm(
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextFormField(
                controller: _firstName,
                decoration: InputDecoration(labelText: l.registerFirstName),
                textInputAction: TextInputAction.next,
                validator: _required,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _lastName,
                decoration: InputDecoration(labelText: l.registerLastName),
                textInputAction: TextInputAction.next,
                validator: _required,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _email,
                decoration: InputDecoration(labelText: l.commonEmail),
                keyboardType: TextInputType.emailAddress,
                textInputAction: TextInputAction.next,
                validator: (value) => (value == null || !value.contains('@')) ? l.commonInvalidEmail : null,
              ),
              const SizedBox(height: 16),
              PasswordFormField(
                controller: _password,
                helperText: l.passwordMinLength(minPasswordLength),
                autofillHints: const [AutofillHints.newPassword],
                validator: (value) =>
                    (value == null || value.length < minPasswordLength) ? l.passwordMinLength(minPasswordLength) : null,
              ),
              const SizedBox(height: 16),
              PasswordFormField(
                labelText: l.passwordRepeat,
                onFieldSubmitted: (_) => _submit(),
                validator: (value) => value != _password.text ? l.passwordMismatch : null,
              ),
              const SizedBox(height: 16),
              if (_error != null) ...[
                ErrorText(message: _error!),
                const SizedBox(height: 16),
              ],
              FilledButton(
                onPressed: _busy ? null : _submit,
                child: _busy ? const ButtonProgress() : Text(l.registerTitle),
              ),
              TextButton(
                onPressed: _busy ? null : () => context.go(Routes.login),
                child: Text(l.registerHaveAccount),
              ),
              const SizedBox(height: 8),
              Text(l.registerPrivacyConsent, textAlign: TextAlign.center),
              PrivacyPolicyLink(label: l.registerReadPrivacyPolicy),
            ],
          ),
        ),
      ),
    );
  }
}
