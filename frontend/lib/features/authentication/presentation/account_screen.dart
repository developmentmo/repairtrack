import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/session_controller.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/l10n/locale_controller.dart';
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
    final l = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(l.accountTitle)),
      body: CenteredForm(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (user != null) ...[
              DetailRow(label: l.accountName, value: '${user.firstName} ${user.lastName}'),
              DetailRow(label: l.accountEmail, value: user.email),
              const SizedBox(height: 32),
            ],
            Text(l.accountLanguage, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            const _LanguageChoice(),
            const SizedBox(height: 32),
            Text(l.accountDeleteTitle, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            Text(l.accountDeleteExplanation),
            const SizedBox(height: 16),
            OutlinedButton.icon(
              key: const Key('delete-account'),
              style: OutlinedButton.styleFrom(foregroundColor: Theme.of(context).colorScheme.error),
              onPressed: () => showDialog<void>(context: context, builder: (_) => const _DeleteAccountDialog()),
              icon: const Icon(Icons.delete_forever_outlined),
              label: Text(l.accountDeleteTitle),
            ),
            const SizedBox(height: 32),
            const Align(alignment: Alignment.centerLeft, child: PrivacyPolicyLink()),
          ],
        ),
      ),
    );
  }
}

/// Dutch or English. Each language is named in its own language, so it can be found whatever is shown now.
class _LanguageChoice extends ConsumerWidget {
  const _LanguageChoice();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final shown = AppLanguage.fromCode(Localizations.localeOf(context).languageCode) ?? AppLanguage.dutch;
    return Align(
      alignment: Alignment.centerLeft,
      child: SegmentedButton<AppLanguage>(
        key: const Key('language-choice'),
        segments: const [
          ButtonSegment(value: AppLanguage.dutch, label: Text('Nederlands')),
          ButtonSegment(value: AppLanguage.english, label: Text('English')),
        ],
        selected: {shown},
        showSelectedIcon: false,
        onSelectionChanged: (selection) => ref.read(languageControllerProvider.notifier).select(selection.single),
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
      setState(() => _error = context.l10n.accountDeletePasswordRequired);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    final messenger = ScaffoldMessenger.of(context);
    final l = context.l10n;
    try {
      await ref.read(authApiProvider).deleteAccount(_password.text);
      if (mounted) {
        Navigator.of(context).pop();
      }
      messenger.showSnackBar(SnackBar(content: Text(l.accountDeleted)));
      await ref.read(sessionControllerProvider.notifier).accountDeleted();
    } catch (e) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = userMessage(l, e);
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return AlertDialog(
      title: Text(l.accountDeleteConfirmTitle),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(l.accountDeleteConfirmText),
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
        TextButton(onPressed: _busy ? null : () => Navigator.of(context).pop(), child: Text(l.commonCancel)),
        FilledButton(
          key: const Key('delete-account-confirm'),
          style: FilledButton.styleFrom(backgroundColor: Theme.of(context).colorScheme.error),
          onPressed: _busy ? null : _delete,
          child: _busy ? const ButtonProgress() : Text(l.commonDelete),
        ),
      ],
    );
  }
}
