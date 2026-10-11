import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/l10n/l10n.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/form_widgets.dart';
import '../application/garage_providers.dart';
import '../data/garage_api.dart';
import '../domain/garage.dart';

/// Register a garage. The caller becomes its admin; RepairTrack verifies it (KvK) before its records
/// count as "verified garage".
class CreateGarageScreen extends ConsumerStatefulWidget {
  const CreateGarageScreen({super.key});

  @override
  ConsumerState<CreateGarageScreen> createState() => _CreateGarageScreenState();
}

class _CreateGarageScreenState extends ConsumerState<CreateGarageScreen> {
  final _formKey = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _kvk = TextEditingController();
  final _address = TextEditingController();
  final _postalCode = TextEditingController();
  final _city = TextEditingController();
  final _phone = TextEditingController();
  final _email = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    for (final controller in [_name, _kvk, _address, _postalCode, _city, _phone, _email]) {
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
      final garage = await ref.read(garageApiProvider).register(
            NewGarage(
              name: _name.text.trim(),
              kvkNumber: _kvk.text.trim(),
              address: _address.text.trim(),
              postalCode: _postalCode.text.trim(),
              city: _city.text.trim(),
              phone: _phone.text.trim(),
              email: _email.text.trim(),
            ),
          );
      ref.invalidate(myGaragesProvider);
      if (mounted) {
        context.go(Routes.garage(garage.id));
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
    return Scaffold(
      appBar: AppBar(title: Text(l.navRegisterGarage)),
      body: CenteredForm(
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              InfoBanner(message: l.createGarageInfo),
              const SizedBox(height: 24),
              TextFormField(
                controller: _name,
                decoration: InputDecoration(labelText: l.createGarageName),
                validator: _required,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _kvk,
                decoration: InputDecoration(labelText: l.createGarageKvk),
                keyboardType: TextInputType.number,
                validator: (value) => isValidKvkNumber(value ?? '') ? null : l.createGarageKvkInvalid,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _address,
                decoration: InputDecoration(labelText: l.createGarageAddress),
                validator: _required,
              ),
              const SizedBox(height: 16),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SizedBox(
                    width: 140,
                    child: TextFormField(
                      controller: _postalCode,
                      decoration: InputDecoration(labelText: l.createGaragePostalCode),
                      textCapitalization: TextCapitalization.characters,
                      validator: (value) => isValidPostalCode(value ?? '') ? null : l.createGaragePostalCodeInvalid,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: TextFormField(
                      controller: _city,
                      decoration: InputDecoration(labelText: l.createGarageCity),
                      validator: _required,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _phone,
                decoration: InputDecoration(labelText: l.createGaragePhone),
                keyboardType: TextInputType.phone,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _email,
                decoration: InputDecoration(labelText: l.createGarageEmail),
                keyboardType: TextInputType.emailAddress,
              ),
              const SizedBox(height: 16),
              if (_error != null) ...[
                ErrorText(message: _error!),
                const SizedBox(height: 16),
              ],
              FilledButton(
                onPressed: _busy ? null : _submit,
                child: _busy ? const ButtonProgress() : Text(l.createGarageSubmit),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
