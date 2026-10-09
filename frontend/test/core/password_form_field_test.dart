import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:repairtrack_app/core/widgets/form_widgets.dart';

void main() {
  testWidgets('the eye shows and hides the password', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: Scaffold(body: PasswordFormField())));

    EditableText field() => tester.widget<EditableText>(find.byType(EditableText));

    expect(field().obscureText, isTrue);
    await tester.tap(find.byTooltip('Wachtwoord tonen'));
    await tester.pump();
    expect(field().obscureText, isFalse);
    await tester.tap(find.byTooltip('Wachtwoord verbergen'));
    await tester.pump();
    expect(field().obscureText, isTrue);
  });
}
