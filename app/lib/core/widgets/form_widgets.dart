import 'package:flutter/material.dart';

/// Scrollable, centered column with a max width: forms look right on phones, tablets and the web.
class CenteredForm extends StatelessWidget {
  const CenteredForm({super.key, required this.child, this.maxWidth = 480});

  final Widget child;
  final double maxWidth;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(constraints: BoxConstraints(maxWidth: maxWidth), child: child),
        ),
      ),
    );
  }
}

class ErrorText extends StatelessWidget {
  const ErrorText({super.key, required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Text(message, style: TextStyle(color: Theme.of(context).colorScheme.error));
  }
}

class InfoBanner extends StatelessWidget {
  const InfoBanner({super.key, required this.message, this.icon = Icons.info_outline, this.warning = false});

  final String message;
  final IconData icon;
  final bool warning;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final background = warning ? scheme.errorContainer : scheme.secondaryContainer;
    final foreground = warning ? scheme.onErrorContainer : scheme.onSecondaryContainer;
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(12)),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: foreground),
          const SizedBox(width: 12),
          Expanded(child: Text(message, style: TextStyle(color: foreground))),
        ],
      ),
    );
  }
}

class ButtonProgress extends StatelessWidget {
  const ButtonProgress({super.key});

  @override
  Widget build(BuildContext context) {
    return const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2));
  }
}

/// Read-only label/value row for detail screens.
class DetailRow extends StatelessWidget {
  const DetailRow({super.key, required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: 140, child: Text(label, style: Theme.of(context).textTheme.bodySmall)),
          Expanded(child: Text(value)),
        ],
      ),
    );
  }
}
