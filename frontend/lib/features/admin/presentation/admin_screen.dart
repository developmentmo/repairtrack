import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/widgets/async_value_view.dart';
import '../../../core/widgets/form_widgets.dart';
import '../../disputes/presentation/admin_disputes_tab.dart';
import '../../garages/domain/garage.dart';
import '../data/admin_api.dart';
import '../domain/admin_user.dart';

/// SYSTEM_ADMIN: garage verification queue, user blocking and ownership disputes. Every action is audited by the backend.
class AdminScreen extends StatelessWidget {
  const AdminScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Beheer'),
          bottom: const TabBar(tabs: [Tab(text: 'Garages'), Tab(text: 'Gebruikers'), Tab(text: 'Geschillen')]),
        ),
        body: const TabBarView(children: [_GarageQueue(), _UserAdmin(), AdminDisputesTab()]),
      ),
    );
  }
}

class _GarageQueue extends ConsumerWidget {
  const _GarageQueue();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final garages = ref.watch(pendingGaragesProvider);
    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(pendingGaragesProvider);
        await ref.read(pendingGaragesProvider.future);
      },
      child: AsyncValueView(
        value: garages,
        onRetry: () => ref.invalidate(pendingGaragesProvider),
        data: (list) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            const Text('Garages die wachten op verificatie. Controleer de KvK-gegevens voordat je verifieert.'),
            const SizedBox(height: 12),
            if (list.isEmpty) const Text('Er wachten geen garages.'),
            for (final garage in list) _GarageCard(garage: garage),
          ],
        ),
      ),
    );
  }
}

class _GarageCard extends ConsumerWidget {
  const _GarageCard({required this.garage});

  final Garage garage;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(garage.name, style: Theme.of(context).textTheme.titleMedium),
            Text('KvK ${garage.kvkNumber} · ${garage.address}, ${garage.postalCode} ${garage.city}'),
            if (garage.email != null || garage.phone != null)
              Text([garage.email, garage.phone].whereType<String>().join(' · ')),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: [
                FilledButton(
                  onPressed: () => _decide(context, ref, 'VERIFIED', 'Garage geverifieerd.'),
                  child: const Text('Verifiëren'),
                ),
                OutlinedButton(
                  onPressed: () => _decide(context, ref, 'UNVERIFIED', 'Garage afgewezen.', askNote: true),
                  child: const Text('Afwijzen'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _decide(BuildContext context, WidgetRef ref, String status, String done, {bool askNote = false}) async {
    String? note;
    if (askNote) {
      note = await showDialog<String>(context: context, builder: (context) => const _NoteDialog());
      if (note == null) {
        return;
      }
    }
    if (!context.mounted) {
      return;
    }
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(adminApiProvider).decideGarage(garage.id, status, note: note);
      ref.invalidate(pendingGaragesProvider);
      messenger.showSnackBar(SnackBar(content: Text(done)));
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(userMessage(e))));
    }
  }
}

class _NoteDialog extends StatefulWidget {
  const _NoteDialog();

  @override
  State<_NoteDialog> createState() => _NoteDialogState();
}

class _NoteDialogState extends State<_NoteDialog> {
  final _note = TextEditingController();

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Reden van afwijzing'),
      content: TextField(
        controller: _note,
        decoration: const InputDecoration(hintText: 'Bijv. KvK-nummer hoort niet bij deze naam'),
        maxLength: 500,
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Annuleren')),
        FilledButton(onPressed: () => Navigator.pop(context, _note.text.trim()), child: const Text('Afwijzen')),
      ],
    );
  }
}

class _UserAdmin extends ConsumerStatefulWidget {
  const _UserAdmin();

  @override
  ConsumerState<_UserAdmin> createState() => _UserAdminState();
}

class _UserAdminState extends ConsumerState<_UserAdmin> {
  final _email = TextEditingController();
  AdminUser? _user;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    super.dispose();
  }

  Future<void> _run(Future<AdminUser> Function() call) async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final user = await call();
      if (mounted) {
        setState(() => _user = user);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = userMessage(e);
          _user = null;
        });
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _toggleBlock(AdminUser user) async {
    final block = !user.isBlocked;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(block ? 'Gebruiker blokkeren?' : 'Blokkade opheffen?'),
        content: Text(block
            ? '${user.email} wordt direct uitgelogd en kan niet meer inloggen.'
            : '${user.email} kan weer inloggen.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Annuleren')),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(block ? 'Blokkeren' : 'Opheffen'),
          ),
        ],
      ),
    );
    if (confirmed != true) {
      return;
    }
    final api = ref.read(adminApiProvider);
    await _run(() => block ? api.block(user.id) : api.unblock(user.id));
  }

  @override
  Widget build(BuildContext context) {
    final user = _user;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        TextField(
          controller: _email,
          decoration: InputDecoration(
            labelText: 'E-mailadres (exact)',
            suffixIcon: IconButton(
              icon: const Icon(Icons.search),
              onPressed: _busy ? null : () => _run(() => ref.read(adminApiProvider).findUser(_email.text.trim())),
            ),
          ),
          keyboardType: TextInputType.emailAddress,
          onSubmitted: (_) => _run(() => ref.read(adminApiProvider).findUser(_email.text.trim())),
        ),
        const SizedBox(height: 16),
        if (_busy) const LinearProgressIndicator(),
        if (_error != null) ErrorText(message: _error!),
        if (user != null)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('${user.firstName} ${user.lastName}', style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 4),
                  DetailRow(label: 'E-mailadres', value: user.email),
                  DetailRow(label: 'Status', value: user.isBlocked ? 'Geblokkeerd' : user.status),
                  DetailRow(label: 'E-mail bevestigd', value: user.emailVerified ? 'Ja' : 'Nee'),
                  DetailRow(label: 'Rollen', value: user.roles.join(', ')),
                  DetailRow(label: 'Aangemaakt', value: formatDateTime(user.createdAt)),
                  const SizedBox(height: 8),
                  if (user.status != 'DELETED')
                    FilledButton.tonal(
                      onPressed: _busy ? null : () => _toggleBlock(user),
                      child: Text(user.isBlocked ? 'Blokkade opheffen' : 'Blokkeren'),
                    ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}
