import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/l10n/l10n.dart';
import '../domain/repair.dart';

/// Asks for one part (description, brand, part number, quantity).
class PartDialog extends StatefulWidget {
  const PartDialog({super.key});

  @override
  State<PartDialog> createState() => _PartDialogState();
}

class _PartDialogState extends State<PartDialog> {
  final _formKey = GlobalKey<FormState>();
  final _description = TextEditingController();
  final _brand = TextEditingController();
  final _partNumber = TextEditingController();
  final _quantity = TextEditingController(text: '1');

  @override
  void dispose() {
    for (final controller in [_description, _brand, _partNumber, _quantity]) {
      controller.dispose();
    }
    super.dispose();
  }

  void _save() {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    Navigator.pop(
      context,
      NewPart(
        description: _description.text.trim(),
        brand: _brand.text.trim(),
        partNumber: _partNumber.text.trim(),
        quantity: int.parse(_quantity.text),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return AlertDialog(
      title: Text(l.partDialogTitle),
      content: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextFormField(
              controller: _description,
              decoration: InputDecoration(labelText: l.commonDescription),
              validator: (value) => (value == null || value.trim().isEmpty) ? l.commonRequiredField : null,
            ),
            const SizedBox(height: 12),
            TextFormField(controller: _brand, decoration: InputDecoration(labelText: l.partDialogBrand)),
            const SizedBox(height: 12),
            TextFormField(
              controller: _partNumber,
              decoration: InputDecoration(labelText: l.partDialogPartNumber),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _quantity,
              decoration: InputDecoration(labelText: l.partDialogQuantity),
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              validator: (value) {
                final quantity = int.tryParse(value ?? '');
                return (quantity == null || quantity < 1 || quantity > 999) ? l.partDialogQuantityRange : null;
              },
            ),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(l.commonCancel)),
        FilledButton(onPressed: _save, child: Text(l.commonAdd)),
      ],
    );
  }
}
