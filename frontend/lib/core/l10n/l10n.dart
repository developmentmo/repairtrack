import 'package:flutter/widgets.dart';

import '../../l10n/app_localizations.dart';

export '../../l10n/app_localizations.dart';

extension L10nContext on BuildContext {
  /// The app's texts in the chosen language.
  AppLocalizations get l10n => AppLocalizations.of(this);
}
