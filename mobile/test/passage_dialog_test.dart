import 'package:archive_fever/screens/passage_dialog.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  /// Opens the dialog from a button and records what it returned and what it tried to save.
  Future<({List<(String, int?)> saves, List<bool> results})> open(
    WidgetTester tester, {
    String text = '어둠 속에서 손을 뻗었다.',
    int? page = 127,
    bool Function()? succeeds,
  }) async {
    final saves = <(String, int?)>[];
    final results = <bool>[];
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: Builder(
          builder: (context) => TextButton(
            onPressed: () async {
              results.add(await showPassageDialog(
                context,
                text: text,
                page: page,
                save: (text, page) async {
                  saves.add((text, page));
                  return succeeds?.call() ?? true;
                },
              ));
            },
            child: const Text('open'),
          ),
        ),
      ),
    ));
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    return (saves: saves, results: results);
  }

  Finder textField() => find.byKey(const Key('passage-text'));
  Finder pageField() => find.byKey(const Key('passage-page'));

  testWidgets('shows the recognised text and page, and saves them as corrected', (tester) async {
    final record = await open(tester);

    expect(tester.widget<TextField>(textField()).controller!.text, '어둠 속에서 손을 뻗었다.');
    expect(tester.widget<TextField>(pageField()).controller!.text, '127');
    expect(find.text('사진에서 읽은 쪽수예요. 맞는지 확인해 주세요.'), findsOneWidget);

    await tester.enterText(textField(), '  어둠 속에서 손을 뻗었다, 천천히.  ');
    await tester.enterText(pageField(), '128');
    await tester.tap(find.text('저장'));
    await tester.pumpAndSettle();

    expect(record.saves, [('어둠 속에서 손을 뻗었다, 천천히.', 128)]);
    expect(record.results, [true]);
    expect(find.byType(AlertDialog), findsNothing);
  });

  testWidgets('leaves the page empty when none was found, and saves without one', (tester) async {
    final record = await open(tester, page: null);

    expect(tester.widget<TextField>(pageField()).controller!.text, isEmpty);
    expect(find.text('모르면 비워 두어도 돼요.'), findsOneWidget);

    await tester.tap(find.text('저장'));
    await tester.pumpAndSettle();

    expect(record.saves, [('어둠 속에서 손을 뻗었다.', null)]);
    expect(record.results, [true]);
  });

  testWidgets('refuses empty text and page zero without calling the server', (tester) async {
    final record = await open(tester);

    await tester.enterText(textField(), '   ');
    await tester.tap(find.text('저장'));
    await tester.pumpAndSettle();
    expect(find.text('저장할 문장이 없어요.'), findsOneWidget);

    await tester.enterText(textField(), '문장.');
    await tester.enterText(pageField(), '0');
    await tester.tap(find.text('저장'));
    await tester.pumpAndSettle();
    expect(find.text('쪽수는 1 이상의 숫자로 적어 주세요.'), findsOneWidget);

    expect(record.saves, isEmpty);
    expect(find.byType(AlertDialog), findsOneWidget);
  });

  testWidgets('only digits can be typed as the page', (tester) async {
    await open(tester, page: null);

    await tester.enterText(pageField(), 'p.12a');

    expect(tester.widget<TextField>(pageField()).controller!.text, '12');
  });

  testWidgets('stays open with the text when saving fails, and can be tried again', (tester) async {
    var online = false;
    final record = await open(tester, succeeds: () => online);

    await tester.enterText(textField(), '고친 문장.');
    await tester.tap(find.text('저장'));
    await tester.pumpAndSettle();

    expect(find.text('저장하지 못했어요. 다시 시도해 주세요.'), findsOneWidget);
    expect(tester.widget<TextField>(textField()).controller!.text, '고친 문장.');
    expect(record.results, isEmpty);

    online = true;
    await tester.tap(find.text('저장'));
    await tester.pumpAndSettle();

    expect(record.saves, [('고친 문장.', 127), ('고친 문장.', 127)]);
    expect(record.results, [true]);
  });

  testWidgets('cancel saves nothing', (tester) async {
    final record = await open(tester);

    await tester.tap(find.text('취소'));
    await tester.pumpAndSettle();

    expect(record.saves, isEmpty);
    expect(record.results, [false]);
  });
}
