import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/session_controller.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/widgets/form_widgets.dart';
import '../data/auth_api.dart';

/// The signed-in user's account. Also the page the App Store / Google Play listing links to for account deletion
/// (`https://<domain>/account`).
class AccountScreen extends ConsumerWidget {
  const AccountScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionControllerProvider);
    final user = session is SignedIn ? session.user : null;
    return Scaffold(
      appBar: AppBar(title: const Text('Account')),
      body: CenteredForm(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (user != null) ...[
              DetailRow(label: 'Naam', value: '${user.firstName} ${user.lastName}'),
              DetailRow(label: 'E-mail', value: user.email),
              const SizedBox(height: 32),
            ],
            Text('Account verwijderen', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            const Text(
              'Je naam en e-mailadres worden verwijderd en je kunt niet meer inloggen. De onderhoudshistorie '
              'hoort bij het voertuig en blijft bewaard, zonder jouw gegevens; je voertuigen komen vrij voor hun '
              'volgende eigenaar en je deellinks werken niet meer. Dit kan niet ongedaan worden gemaakt.',
            ),
            const SizedBox(height: 16),
            OutlinedButton.icon(
              key: const Key('delete-account'),
              style: OutlinedButton.styleFrom(foregroundColor: Theme.of(context).colorScheme.error),
              onPressed: () => showDialog<void>(context: context, builder: (_) => const _DeleteAccountDialog()),
              icon: const Icon(Icons.delete_forever_outlined),
              label: const Text('Account verwijderen'),
            ),
            const SizedBox(height: 32),
            const Align(alignment: Alignment.centerLeft, child: PrivacyPolicyLink()),
          ],
        ),
      ),
    );
  }
}

class _DeleteAccountDialog extends ConsumerStatefulWidget {
  const _DeleteAccountDialog();

  @override
  ConsumerState<_DeleteAccountDialog> createState() => _DeleteAccountDialogState();
}

class _DeleteAccountDialogState extends ConsumerState<_DeleteAccountDialog> {
  final _password = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _password.dispose();
    super.dispose();
  }

  Future<void> _delete() async {
    if (_password.text.isEmpty) {
      setState(() => _error = 'Vul je wachtwoord in.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(authApiProvider).deleteAccount(_password.text);
      if (mounted) {
        Navigator.of(context).pop();
      }
      messenger.showSnackBar(const SnackBar(content: Text('Je account is verwijderd.')));
      await ref.read(sessionControllerProvider.notifier).accountDeleted();
    } catch (e) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = userMessage(e);
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Account definitief verwijderen?'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Text('Vul ter bevestiging je wachtwoord in.'),
          const SizedBox(height: 12),
          PasswordFormField(
            key: const Key('delete-account-password'),
            controller: _password,
            autofillHints: const [AutofillHints.password],
            onFieldSubmitted: (_) {
              if (!_busy) {
                _delete();
              }
            },
          ),
          if (_error != null) ...[
            const SizedBox(height: 12),
            ErrorText(message: _error!),
          ],
        ],
      ),
      actions: [
        TextButton(onPressed: _busy ? null : () => Navigator.of(context).pop(), child: const Text('Annuleren')),
        FilledButton(
          key: const Key('delete-account-confirm'),
          style: FilledButton.styleFrom(backgroundColor: Theme.of(context).colorScheme.error),
          onPressed: _busy ? null : _delete,
          child: _busy ? const ButtonProgress() : const Text('Verwijderen'),
        ),
      ],
    );
  }
}
