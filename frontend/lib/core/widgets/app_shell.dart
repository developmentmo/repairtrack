import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/garages/application/garage_providers.dart';
import '../../features/garages/domain/garage.dart';
import '../auth/session_controller.dart';
import '../routing/routes.dart';
import '../theme/app_theme.dart';
import 'brand_widgets.dart';

/// From this width the app shows a sidebar instead of a bottom navigation bar.
const wideLayoutBreakpoint = 900.0;

bool isWideLayout(BuildContext context) => MediaQuery.sizeOf(context).width >= wideLayoutBreakpoint;

class _Destination {
  const _Destination(this.label, this.icon, this.selectedIcon, this.route);

  final String label;
  final IconData icon;
  final IconData selectedIcon;
  final String route;
}

const _dashboard = _Destination('Dashboard', Icons.home_outlined, Icons.home, Routes.home);
const _disputes = _Destination('Geschillen', Icons.gavel_outlined, Icons.gavel, Routes.disputes);
const _account = _Destination('Account', Icons.person_outline, Icons.person, Routes.account);
const _admin = _Destination('Beheer', Icons.admin_panel_settings_outlined, Icons.admin_panel_settings, Routes.admin);

/// Frame around every signed-in screen: a sidebar on wide screens, a bottom navigation bar on phones.
class AppShell extends ConsumerWidget {
  const AppShell({super.key, required this.location, required this.child});

  /// The current (matched) location, to highlight the active destination.
  final String location;
  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionControllerProvider);
    final isSystemAdmin = session is SignedIn && session.user.isSystemAdmin;
    final destinations = [_dashboard, _disputes, _account, if (isSystemAdmin) _admin];

    if (!isWideLayout(context)) {
      final selected = destinations.indexWhere((d) => _isActive(d.route));
      return Scaffold(
        body: child,
        bottomNavigationBar: NavigationBar(
          height: 64,
          selectedIndex: selected < 0 ? 0 : selected,
          onDestinationSelected: (index) => context.go(destinations[index].route),
          destinations: [
            for (final d in destinations)
              NavigationDestination(icon: Icon(d.icon), selectedIcon: Icon(d.selectedIcon), label: d.label),
          ],
        ),
      );
    }

    final garages = ref.watch(myGaragesProvider).value ?? const <MyGarage>[];
    return Scaffold(
      body: Row(
        children: [
          _Sidebar(
            location: location,
            destinations: destinations,
            garages: garages,
            isActive: _isActive,
          ),
          VerticalDivider(width: 1, color: Theme.of(context).colorScheme.outlineVariant),
          Expanded(child: child),
        ],
      ),
    );
  }

  bool _isActive(String route) {
    if (route == Routes.home) {
      return location == Routes.home || location.startsWith('/vehicles') || location.startsWith('/repairs');
    }
    return location == route || location.startsWith('$route/');
  }
}

class _Sidebar extends ConsumerWidget {
  const _Sidebar({
    required this.location,
    required this.destinations,
    required this.garages,
    required this.isActive,
  });

  final String location;
  final List<_Destination> destinations;
  final List<MyGarage> garages;
  final bool Function(String route) isActive;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionControllerProvider);
    final user = session is SignedIn ? session.user : null;
    return Container(
      width: 248,
      color: Theme.of(context).colorScheme.surface,
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 24),
              child: Align(alignment: Alignment.centerLeft, child: BrandLogo(size: 30)),
            ),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.symmetric(horizontal: 12),
                children: [
                  for (final d in destinations.where((d) => d != _account))
                    _SidebarItem(
                      label: d.label,
                      icon: isActive(d.route) ? d.selectedIcon : d.icon,
                      selected: isActive(d.route),
                      onTap: () => context.go(d.route),
                    ),
                  const SizedBox(height: 16),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(12, 0, 12, 6),
                    child: Text('Garages', style: Theme.of(context).textTheme.labelSmall?.copyWith(color: BrandColors.mutedText)),
                  ),
                  for (final garage in garages)
                    _SidebarItem(
                      label: garage.name,
                      icon: Icons.home_repair_service_outlined,
                      selected: location.startsWith(Routes.garage(garage.garageId)),
                      onTap: () => context.go(Routes.garage(garage.garageId)),
                    ),
                  _SidebarItem(
                    label: 'Garage aanmelden',
                    icon: Icons.add_business_outlined,
                    selected: location == Routes.newGarage,
                    onTap: () => context.go(Routes.newGarage),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Column(
                children: [
                  _SidebarItem(
                    label: 'Account',
                    icon: isActive(Routes.account) ? Icons.person : Icons.person_outline,
                    selected: isActive(Routes.account),
                    onTap: () => context.go(Routes.account),
                  ),
                  _SidebarItem(
                    label: 'Uitloggen',
                    icon: Icons.logout,
                    selected: false,
                    onTap: () => ref.read(sessionControllerProvider.notifier).logout(),
                  ),
                ],
              ),
            ),
            if (user != null) ...[
              const Divider(height: 24),
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
                child: Row(
                  children: [
                    UserAvatar(initials: _initials(user.firstName, user.lastName)),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '${user.firstName} ${user.lastName}',
                            style: Theme.of(context).textTheme.titleSmall,
                            overflow: TextOverflow.ellipsis,
                          ),
                          Text(
                            user.isSystemAdmin ? 'Beheerder' : 'Eigenaar',
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

String _initials(String firstName, String lastName) =>
    [firstName, lastName].where((n) => n.isNotEmpty).map((n) => n.characters.first.toUpperCase()).join();

class _SidebarItem extends StatelessWidget {
  const _SidebarItem({required this.label, required this.icon, required this.selected, required this.onTap});

  final String label;
  final IconData icon;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final (activeForeground, activeBackground) = toneColors(context, Tone.info);
    final foreground = selected ? activeForeground : Theme.of(context).textTheme.bodyMedium?.color;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Material(
        color: selected ? activeBackground : Colors.transparent,
        borderRadius: BorderRadius.circular(10),
        child: InkWell(
          borderRadius: BorderRadius.circular(10),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            child: Row(
              children: [
                Icon(icon, size: 20, color: foreground),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    label,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: foreground,
                      fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Round avatar with the user's initials.
class UserAvatar extends StatelessWidget {
  const UserAvatar({super.key, required this.initials, this.size = 36});

  final String initials;
  final double size;

  @override
  Widget build(BuildContext context) {
    return CircleAvatar(
      radius: size / 2,
      backgroundColor: BrandColors.navy,
      foregroundColor: Colors.white,
      child: Text(initials, style: TextStyle(fontSize: size * 0.38, fontWeight: FontWeight.w600)),
    );
  }
}

/// Initials for [UserAvatar].
String userInitials(String firstName, String lastName) => _initials(firstName, lastName);
