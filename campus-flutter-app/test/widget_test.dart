import 'package:flutter_test/flutter_test.dart';

import 'package:campus_flutter_app/main.dart';

void main() {
  testWidgets('renders CampusMind shell', (WidgetTester tester) async {
    await tester.pumpWidget(const CampusMindApp());

    expect(find.text('CampusMind'), findsOneWidget);
  });
}
