import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

/// The RepairTrack mark (car with wrench) followed by the "RepairTrack" word mark, as in the logo.
class BrandLogo extends StatelessWidget {
  const BrandLogo({super.key, this.size = 28, this.showName = true});

  /// Height of the mark; the word mark scales with it.
  final double size;
  final bool showName;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Semantics(
      label: 'RepairTrack',
      excludeSemantics: true,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Image.asset(
            dark ? 'assets/branding/logo_mark_dark.png' : 'assets/branding/logo_mark.png',
            height: size,
            filterQuality: FilterQuality.medium,
          ),
          if (showName) ...[
            SizedBox(width: size * 0.3),
            Flexible(
              child: FittedBox(
                fit: BoxFit.scaleDown,
                child: Text.rich(
                  TextSpan(
                    children: [
                      TextSpan(text: 'Repair', style: TextStyle(color: dark ? Colors.white : BrandColors.navy)),
                      const TextSpan(text: 'Track', style: TextStyle(color: BrandColors.blue)),
                    ],
                  ),
                  style: TextStyle(fontSize: size * 0.72, fontWeight: FontWeight.w800, letterSpacing: -0.5),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// The full stacked logo (mark above the word mark), for the login and splash screens.
class BrandLogoLarge extends StatelessWidget {
  const BrandLogoLarge({super.key, this.width = 220});

  final double width;

  @override
  Widget build(BuildContext context) {
    if (Theme.of(context).brightness == Brightness.dark) {
      return Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Image.asset('assets/branding/logo_mark_dark.png', width: width * 0.6),
          const SizedBox(height: 12),
          Text.rich(
            const TextSpan(
              children: [
                TextSpan(text: 'Repair', style: TextStyle(color: Colors.white)),
                TextSpan(text: 'Track', style: TextStyle(color: BrandColors.blue)),
              ],
            ),
            style: TextStyle(fontSize: width * 0.2, fontWeight: FontWeight.w800, letterSpacing: -0.5),
          ),
        ],
      );
    }
    return Image.asset('assets/branding/logo_full.png', width: width, semanticLabel: 'RepairTrack');
  }
}

/// Colour families for status pills and icon badges.
enum Tone { success, info, warning, danger, purple, neutral }

/// Foreground and background colours for a [Tone], in light and dark mode.
(Color foreground, Color background) toneColors(BuildContext context, Tone tone) {
  final dark = Theme.of(context).brightness == Brightness.dark;
  final foreground = switch (tone) {
    Tone.success => dark ? const Color(0xFF4ADE80) : const Color(0xFF15803D),
    Tone.info => dark ? const Color(0xFF60A5FA) : BrandColors.blue,
    Tone.warning => dark ? const Color(0xFFFBBF24) : const Color(0xFFC2410C),
    Tone.danger => dark ? const Color(0xFFF87171) : const Color(0xFFB91C1C),
    Tone.purple => dark ? const Color(0xFFC4B5FD) : const Color(0xFF7C3AED),
    Tone.neutral => dark ? Colors.white70 : BrandColors.mutedText,
  };
  final background = switch (tone) {
    Tone.success => dark ? const Color(0x3322C55E) : const Color(0xFFE8F7EE),
    Tone.info => dark ? const Color(0x333B82F6) : const Color(0xFFE8F1FF),
    Tone.warning => dark ? const Color(0x33F59E0B) : const Color(0xFFFFF1E6),
    Tone.danger => dark ? const Color(0x33EF4444) : const Color(0xFFFDECEC),
    Tone.purple => dark ? const Color(0x338B5CF6) : const Color(0xFFF1EBFE),
    Tone.neutral => dark ? const Color(0x22FFFFFF) : const Color(0xFFF1F4F9),
  };
  return (foreground, background);
}

/// Small rounded label such as "Garage bevestigd" or "Onbevestigd".
class StatusPill extends StatelessWidget {
  const StatusPill({super.key, required this.label, required this.tone, this.icon});

  final String label;
  final Tone tone;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final (foreground, background) = toneColors(context, tone);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(20)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 14, color: foreground),
            const SizedBox(width: 4),
          ],
          Text(label, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: foreground)),
        ],
      ),
    );
  }
}

/// An icon in a tinted circle, used in lists and stat tiles.
class IconBadge extends StatelessWidget {
  const IconBadge({super.key, required this.icon, this.tone = Tone.info, this.size = 40});

  final IconData icon;
  final Tone tone;
  final double size;

  @override
  Widget build(BuildContext context) {
    final (foreground, background) = toneColors(context, tone);
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(color: background, shape: BoxShape.circle),
      child: Icon(icon, size: size * 0.5, color: foreground),
    );
  }
}

/// A Dutch licence plate shown as an outlined label ("K-123-AB").
class LicensePlate extends StatelessWidget {
  const LicensePlate({super.key, required this.plate});

  final String plate;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        border: Border.all(color: Theme.of(context).colorScheme.outlineVariant),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        plate,
        style: const TextStyle(fontWeight: FontWeight.w600, letterSpacing: 0.5, fontFeatures: [FontFeature.tabularFigures()]),
      ),
    );
  }
}

/// Stand-in for a vehicle photo: a soft gradient with a car, until vehicles have photos.
class VehiclePicture extends StatelessWidget {
  const VehiclePicture({super.key, this.width, this.height = 120, this.radius = 10});

  final double? width;
  final double height;
  final double radius;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(radius),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: dark
              ? const [Color(0xFF1C2A44), Color(0xFF0F1A2E)]
              : const [Color(0xFFE8F1FF), Color(0xFFD5E4FA)],
        ),
      ),
      child: Icon(
        Icons.directions_car_filled,
        size: height * 0.45,
        color: dark ? const Color(0xFF3B5A8C) : BrandColors.navy.withValues(alpha: 0.25),
      ),
    );
  }
}

/// White panel with a title and an optional action on the right ("Bekijk alle").
class SectionCard extends StatelessWidget {
  const SectionCard({super.key, required this.title, required this.child, this.action, this.padding});

  final String title;
  final Widget child;
  final Widget? action;
  final EdgeInsetsGeometry? padding;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: padding ?? const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(child: Text(title, style: Theme.of(context).textTheme.titleMedium)),
                ?action,
              ],
            ),
            const SizedBox(height: 12),
            child,
          ],
        ),
      ),
    );
  }
}

/// Small blue text button for section headers.
class SectionLink extends StatelessWidget {
  const SectionLink({super.key, required this.label, required this.onPressed});

  final String label;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return TextButton(
      style: TextButton.styleFrom(
        visualDensity: VisualDensity.compact,
        textStyle: Theme.of(context).textTheme.labelLarge?.copyWith(fontSize: 13, fontWeight: FontWeight.w600),
      ),
      onPressed: onPressed,
      child: Text(label),
    );
  }
}

/// Centers content with a maximum width, so wide screens don't stretch everything.
class ContentWidth extends StatelessWidget {
  const ContentWidth({super.key, required this.child, this.maxWidth = 1200});

  final Widget child;
  final double maxWidth;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.topCenter,
      child: ConstrainedBox(constraints: BoxConstraints(maxWidth: maxWidth), child: child),
    );
  }
}
