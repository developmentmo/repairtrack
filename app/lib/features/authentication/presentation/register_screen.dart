import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/auth/session_controller.dart';
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
        setState(() => _error = userMessage(e));
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  String? _required(String? value) => (value == null || value.trim().isEmpty) ? 'Verplicht veld' : null;

  @override
  Widget build(BuildContext context) {
    final registered = _registeredEmail;
    if (registered != null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Bevestig je e-mailadres')),
        body: CenteredForm(child: CheckYourMail(email: registered)),
      );
    }
    return Scaffold(
      appBar: AppBar(title: const Text('Account aanmaken')),
      body: CenteredForm(
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextFormField(
                controller: _firstName,
                decoration: const InputDecoration(labelText: 'Voornaam'),
                textInputAction: TextInputAction.next,
                validator: _required,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _lastName,
                decoration: const InputDecoration(labelText: 'Achternaam'),
                textInputAction: TextInputAction.next,
                validator: _required,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _email,
                decoration: const InputDecoration(labelText: 'E-mailadres'),
                keyboardType: TextInputType.emailAddress,
                textInputAction: TextInputAction.next,
                validator: (value) => (value == null || !value.contains('@')) ? 'Vul een geldig e-mailadres in' : null,
              ),
              const SizedBox(height: 16),
              PasswordFormField(
                controller: _password,
                helperText: 'Minimaal $minPasswordLength tekens',
                autofillHints: const [AutofillHints.newPassword],
                validator: (value) =>
                    (value == null || value.length < minPasswordLength) ? 'Minimaal $minPasswordLength tekens' : null,
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
                child: _busy ? const ButtonProgress() : const Text('Account aanmaken'),
              ),
              TextButton(
                onPressed: _busy ? null : () => context.go(Routes.login),
                child: const Text('Ik heb al een account'),
              ),
              const SizedBox(height: 8),
              const Text(
                'Met het aanmaken van een account ga je akkoord met het verwerken van je gegevens zoals beschreven '
                'in ons privacybeleid.',
                textAlign: TextAlign.center,
              ),
              const PrivacyPolicyLink(label: 'Privacybeleid lezen'),
            ],
          ),
        ),
      ),
    );
  }
}
