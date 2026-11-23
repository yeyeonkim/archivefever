import 'dart:ui' as ui;

import 'package:archive_fever/page_text.dart';
import 'package:archive_fever/screens/page_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  // A 600 x 800 photo in a 300 x 400 display: every photo pixel is half a display pixel.
  // Five lines, 60 photo pixels apart; the last one lies outside what the display shows.
  final lines = [
    for (var i = 0; i < 4; i++) PageLine('줄 ${i + 1}', Rect.fromLTWH(100, 100.0 + i * 60, 400, 40)),
    const PageLine('사진 밖', Rect.fromLTWH(100, 900, 400, 40)),
  ];

  /// The middle of line [index] in the display.
  Offset middle(int index) => Offset(150, 60.0 + index * 30);

  late ui.Image photo;
  late Set<int> picked;
  late int retakes;

  Future<void> show(WidgetTester tester, {List<PageLine>? found, bool unreadable = false}) async {
    photo = await tester.runAsync(() => createTestImage(width: 600, height: 800)) as ui.Image;
    picked = {};
    retakes = 0;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: Align(
          alignment: Alignment.topLeft,
          child: SizedBox(
            width: 300,
            height: 400,
            child: StatefulBuilder(
              builder: (context, setState) => PagePicker(
                photo: photo,
                lines: found,
                picked: picked,
                unreadable: unreadable,
                onChanged: (next) => setState(() => picked = next),
                onRetake: () => retakes++,
              ),
            ),
          ),
        ),
      ),
    ));
  }

  testWidgets('a touch picks a line and another touch lets it go', (tester) async {
    await show(tester, found: lines);
    expect(find.text('저장할 줄을 눌러 고르세요'), findsOneWidget);

    await tester.tapAt(middle(1));
    await tester.pump();
    expect(picked, {1});
    expect(find.text('1줄 선택'), findsOneWidget);

    await tester.tapAt(middle(3));
    await tester.pump();
    expect(picked, {1, 3});

    await tester.tapAt(middle(1));
    await tester.pump();
    expect(picked, {3});
  });

  testWidgets('a drag picks every line it passes, and shrinks when it comes back', (tester) async {
    await show(tester, found: lines);

    final drag = await tester.startGesture(middle(0));
    await drag.moveTo(middle(2));
    await tester.pump();
    expect(picked, {0, 1, 2});

    await drag.moveTo(middle(1));
    await tester.pump();
    expect(picked, {0, 1});

    await drag.up();
    await tester.pump();
    expect(find.text('2줄 선택'), findsOneWidget);
  });

  testWidgets('a drag that starts on a picked line lets lines go', (tester) async {
    await show(tester, found: lines);
    await tester.dragFrom(middle(0), middle(3) - middle(0));
    await tester.pump();
    expect(picked, {0, 1, 2, 3});

    await tester.dragFrom(middle(1), middle(2) - middle(1));
    await tester.pump();
    expect(picked, {0, 3});
  });

  testWidgets('touching between the lines of the photo picks nothing', (tester) async {
    await show(tester, found: lines);

    await tester.tapAt(const Offset(150, 250));
    await tester.pump();

    expect(picked, isEmpty);
  });

  testWidgets('a line outside the display is never picked, even by a drag past the last one', (tester) async {
    await show(tester, found: lines);

    await tester.dragFrom(middle(2), const Offset(0, 300));
    await tester.pump();

    expect(picked, {2, 3});
  });

  testWidgets('says what is going on while reading and when there is nothing to pick', (tester) async {
    await show(tester);
    expect(find.text('글자를 읽는 중…'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    await show(tester, found: const []);
    expect(find.text('글자를 찾지 못했어요'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);

    await show(tester, unreadable: true);
    expect(find.text('글자를 읽지 못했어요'), findsOneWidget);
  });

  testWidgets('retake asks for another photo', (tester) async {
    await show(tester, found: lines);

    await tester.tap(find.text('다시 찍기'));

    expect(retakes, 1);
    expect(picked, isEmpty);
  });
}
