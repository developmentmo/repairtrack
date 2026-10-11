import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/l10n/l10n.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/network/error_messages.dart';
import '../../../core/routing/routes.dart';
import '../../../core/widgets/form_widgets.dart';
import '../../vehicles/domain/vehicle.dart';
import '../application/dispute_providers.dart';
import '../data/dispute_api.dart';
import '../domain/dispute.dart';
import 'evidence_picker.dart';

/// "This vehicle is mine": contest the current owner's claim. The VIN is the same proof as for a claim; the file
/// and the statement are for the RepairTrack admin who decides.
class OpenDisputeScreen extends ConsumerStatefulWidget {
  const OpenDisputeScreen({super.key, required this.vehicleId, this.vehicleName});

  final String vehicleId;
  final String? vehicleName;

  @override
  ConsumerState<OpenDisputeScreen> createState() => _OpenDisputeScreenState();
}

class _OpenDisputeScreenState extends ConsumerState<OpenDisputeScreen> {
  final _formKey = GlobalKey<FormState>();
  final _vin = TextEditingController();
  final _statement = TextEditingController();
  EvidenceFile? _file;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _vin.dispose();
    _statement.dispose();
    super.dispose();
  }

  Future<void> _pick() async {
    final file = await pickEvidenceFile(context.l10n, (message) => setState(() => _error = message));
    if (file != null && mounted) {
      setState(() {
        _file = file;
        _error = null;
      });
    }
  }

  Future<void> _submit() async {
    final l = context.l10n;
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final file = _file;
    if (file == null) {
      setState(() => _error = l.openDisputeFileRequired);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(disputeApiProvider).open(
            widget.vehicleId,
            vin: normalizeVin(_vin.text),
            statement: _statement.text.trim(),
            file: file,
          );
      ref.invalidate(myDisputesProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l.openDisputeSubmitted)),
        );
        context.go(Routes.disputes);
      }
    } on ApiException catch (e) {
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
      appBar: AppBar(title: Text(l.openDisputeTitle)),
      body: CenteredForm(
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              InfoBanner(
                message: l.openDisputeIntro(widget.vehicleName ?? l.openDisputeThisVehicle),
              ),
              const SizedBox(height: 24),
              TextFormField(
                key: const Key('dispute-vin'),
                controller: _vin,
                decoration: InputDecoration(
                  labelText: l.commonVin,
                  helperText: l.openDisputeVinHelper,
                ),
                textCapitalization: TextCapitalization.characters,
                validator: (value) => isValidVin(value ?? '') ? null : l.openDisputeVinInvalid,
              ),
              const SizedBox(height: 16),
              TextFormField(
                key: const Key('dispute-statement'),
                controller: _statement,
                decoration: InputDecoration(
                  labelText: l.commonExplanation,
                  helperText: l.openDisputeStatementHelper,
                  alignLabelWithHint: true,
                ),
                minLines: 4,
                maxLines: 8,
                maxLength: 2000,
                validator: (value) => (value ?? '').trim().length < 10 ? l.disputeStatementTooShort : null,
              ),
              const SizedBox(height: 8),
              Text(l.evidenceHint),
              const SizedBox(height: 8),
              EvidenceFileButton(file: _file, onPressed: _busy ? null : _pick),
              const SizedBox(height: 16),
              if (_error != null) ...[
                ErrorText(message: _error!),
                const SizedBox(height: 16),
              ],
              FilledButton(
                key: const Key('dispute-submit'),
                onPressed: _busy ? null : _submit,
                child: _busy ? const ButtonProgress() : Text(l.openDisputeSubmit),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
