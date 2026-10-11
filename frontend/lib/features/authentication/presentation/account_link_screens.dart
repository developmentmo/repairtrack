import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/l10n/l10n.dart';
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
        Text(context.l10n.checkMailText(email), textAlign: TextAlign.center),
        const SizedBox(height: 24),
        ResendVerificationButton(email: email),
        const SizedBox(height: 8),
        FilledButton(onPressed: () => context.go(Routes.login), child: Text(context.l10n.commonBackToLogin)),
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
    final l = context.l10n;
    setState(() => _busy = true);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(authApiProvider).resendVerification(widget.email);
      if (mounted) {
        setState(() => _sent = true);
      }
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(userMessage(l, e))));
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_sent) {
      return InfoBanner(message: context.l10n.resendVerificationSent);
    }
    return OutlinedButton.icon(
      key: const Key('resend-verification'),
      onPressed: _busy ? null : _resend,
      icon: const Icon(Icons.refresh),
      label: Text(context.l10n.resendVerificationButton),
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
    final l = context.l10n;
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
        setState(() => _error = userMessage(l, e));
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(l.forgotPasswordTitle)),
      body: CenteredForm(
        child: _sent
            ? Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  InfoBanner(message: l.forgotPasswordSent),
                  const SizedBox(height: 16),
                  FilledButton(onPressed: () => context.go(Routes.login), child: Text(l.commonBackToLogin)),
                ],
              )
            : Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    TextFormField(
                      controller: _email,
                      decoration: InputDecoration(labelText: l.commonEmail),
                      keyboardType: TextInputType.emailAddress,
                      onFieldSubmitted: (_) => _submit(),
                      validator: (value) =>
                          (value == null || !value.contains('@')) ? l.commonInvalidEmail : null,
                    ),
                    const SizedBox(height: 16),
                    if (_error != null) ...[
                      ErrorText(message: _error!),
                      const SizedBox(height: 16),
                    ],
                    FilledButton(
                      onPressed: _busy ? null : _submit,
                      child: _busy ? const ButtonProgress() : Text(l.forgotPasswordSubmit),
                    ),
                    TextButton(onPressed: () => context.go(Routes.login), child: Text(l.forgotPasswordBack)),
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
    final l = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(l.verifyEmailTitle), automaticallyImplyLeading: false),
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
                      ? userMessage(l, snapshot.error!)
                      : l.verifyEmailDone,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 24),
                FilledButton(onPressed: () => context.go(Routes.login), child: Text(l.commonBackToLogin)),
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
    final l = context.l10n;
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
        setState(() => _error = userMessage(l, e));
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
    final l = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(l.resetPasswordNewPassword), automaticallyImplyLeading: false),
      body: CenteredForm(
        child: _done
            ? Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  InfoBanner(message: l.resetPasswordDone),
                  const SizedBox(height: 16),
                  FilledButton(onPressed: () => context.go(Routes.login), child: Text(l.commonBackToLogin)),
                ],
              )
            : Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    PasswordFormField(
                      controller: _password,
                      labelText: l.resetPasswordNewPassword,
                      helperText: l.passwordMinLength(min),
                      autofillHints: const [AutofillHints.newPassword],
                      validator: (value) => (value == null || value.length < min) ? l.passwordMinLength(min) : null,
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
                      child: _busy ? const ButtonProgress() : Text(l.resetPasswordSubmit),
                    ),
                  ],
                ),
              ),
      ),
    );
  }
}
