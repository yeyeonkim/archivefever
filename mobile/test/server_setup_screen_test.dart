import 'package:archive_fever/screens/server_setup_screen.dart';
import 'package:archive_fever/settings.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  Future<Settings> pump(WidgetTester tester, http.Client client, {Map<String, Object> saved = const {}}) async {
    SharedPreferences.setMockInitialValues(saved);
    final settings = await Settings.load();
    await tester.pumpWidget(MaterialApp(home: ServerSetupScreen(settings: settings, client: client)));
    return settings;
  }

  testWidgets('text that is not an address is refused without a request', (tester) async {
    var requests = 0;
    await pump(tester, MockClient((request) async {
      requests++;
      return http.Response('{"status":"ok"}', 200);
    }));

    await tester.enterText(find.byType(TextField), 'ftp://nowhere');
    await tester.tap(find.text('접속'));
    await tester.pump();

    expect(find.textContaining('주소를 확인해 주세요'), findsOneWidget);
    expect(requests, 0);
  });

  testWidgets('a server that does not answer is reported and not saved', (tester) async {
    final settings = await pump(tester, MockClient((request) async {
      expect(request.url.toString(), 'http://192.168.0.10:8080/api/health');
      return http.Response('not found', 404);
    }));

    await tester.enterText(find.byType(TextField), '192.168.0.10');
    await tester.tap(find.text('접속'));
    await tester.pumpAndSettle();

    expect(find.textContaining('서버가 응답하지 않아요'), findsOneWidget);
    expect(settings.server, isNull);
    // The form is usable again for another attempt.
    expect(find.text('접속'), findsOneWidget);
  });

  testWidgets('a saved address is offered for editing', (tester) async {
    await pump(tester, MockClient((request) async => http.Response('', 500)),
        saved: {'server': 'http://192.168.0.10:8080'});

    expect(find.text('http://192.168.0.10:8080'), findsOneWidget);
  });
}
