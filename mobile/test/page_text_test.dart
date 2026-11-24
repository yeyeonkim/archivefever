import 'dart:ui';

import 'package:archive_fever/page_text.dart';
import 'package:flutter_test/flutter_test.dart';

/// A page of [texts], one line each, 40 pixels apart from the top of a 1000 x 1400 photo.
List<PageLine> page(List<String> texts) => [
      for (var i = 0; i < texts.length; i++) PageLine(texts[i], Rect.fromLTWH(100, 100.0 + i * 40, 800, 30)),
    ];

void main() {
  group('joinLines', () {
    test('keeps a word broken across two lines whole', () {
      expect(joinLines(['어둠 속에서 손을 뻗', '었다.']), '어둠 속에서 손을 뻗었다.');
      expect(joinLines(['그것은 아름', '다운 일이었다']), '그것은 아름다운 일이었다');
    });

    test('puts a space after a line that ends a word', () {
      expect(joinLines(['너는 그것을', '보았다.']), '너는 그것을 보았다.');
      expect(joinLines(['비가 올 것 같아서', '우산을 챙겼다']), '비가 올 것 같아서 우산을 챙겼다');
    });

    test('puts a space after punctuation, letters and digits', () {
      expect(joinLines(['끝났다.', '다음 날']), '끝났다. 다음 날');
      expect(joinLines(['그는 말했다,', '천천히']), '그는 말했다, 천천히');
      expect(joinLines(['the quick brown', 'fox']), 'the quick brown fox');
      expect(joinLines(['1980', '년 5월']), '1980 년 5월');
    });

    test('keeps brackets and closing marks against their words', () {
      expect(joinLines(['그는 말했다 “', '괜찮아”']), '그는 말했다 “괜찮아”');
      expect(joinLines(['정말 그랬을까', '? 모르겠다']), '정말 그랬을까? 모르겠다');
    });

    test('tidies spacing and skips empty lines', () {
      expect(joinLines(['  첫   줄은  ', '', '   ', '둘째 줄']), '첫 줄은 둘째 줄');
      expect(joinLines([]), '');
    });
  });

  group('guessPageNumber', () {
    test('finds a number alone under the text', () {
      expect(guessPageNumber(page(['첫 줄', '둘째 줄', '셋째 줄', '넷째 줄', '다섯째 줄', '여섯째 줄', '일곱째 줄', '여덟째 줄', '127'])), 127);
    });

    test('finds a number alone above the text, with decoration', () {
      expect(guessPageNumber(page(['— 42 —', '첫 줄', '둘째 줄', '셋째 줄', '넷째 줄', '다섯째 줄', '여섯째 줄', '일곱째 줄', '여덟째 줄'])), 42);
    });

    test('finds the number of a running header', () {
      final lines = ['128 소년이 온다', '첫 줄', '둘째 줄', '셋째 줄', '넷째 줄', '다섯째 줄', '여섯째 줄', '일곱째 줄', '여덟째 줄'];
      expect(guessPageNumber(page(lines)), 128);
      expect(guessPageNumber(page(['2장 일곱 개의 뺨 45', ...lines.skip(1)])), 45);
    });

    test('prefers a number standing alone over one in a header', () {
      final lines = ['3부 꽃 핀 쪽으로 2013', '첫 줄', '둘째 줄', '셋째 줄', '넷째 줄', '다섯째 줄', '여섯째 줄', '일곱째 줄', '99'];
      expect(guessPageNumber(page(lines)), 99);
    });

    test('ignores numbers in the body of the page', () {
      final lines = ['첫 줄', '둘째 줄', '셋째 줄', '1980', '다섯째 줄', '여섯째 줄', '일곱째 줄', '여덟째 줄', '아홉째 줄', '열째 줄'];
      expect(guessPageNumber(page(lines)), isNull);
    });

    test('ignores years in running text and numbers that cannot be pages', () {
      expect(guessPageNumber(page(['1980년 5월의 일이었다', '둘째 줄', '셋째 줄', '넷째 줄', '다섯째 줄', '여섯째 줄', '일곱째 줄', '끝 줄이다'])), isNull);
      expect(guessPageNumber(page(['첫 줄', '둘째 줄', '셋째 줄', '넷째 줄', '다섯째 줄', '여섯째 줄', '일곱째 줄', '여덟째 줄', '007'])), isNull);
      expect(guessPageNumber(page(['첫 줄', '둘째 줄', '셋째 줄', '넷째 줄', '다섯째 줄', '여섯째 줄', '일곱째 줄', '여덟째 줄', '12345'])), isNull);
    });

    test('finds nothing on a close-up of a paragraph, or on no text at all', () {
      expect(guessPageNumber(page(['어둠 속에서', '손을 뻗었다.'])), isNull);
      expect(guessPageNumber([]), isNull);
    });
  });

  group('photoToDisplay', () {
    test('scales a photo that is cropped at the sides', () {
      // 2000 x 1000 photo in a 500 x 500 display: scaled by 0.5, 250 cut off each side.
      const photo = Size(2000, 1000);
      const display = Size(500, 500);
      expect(photoToDisplay(const Rect.fromLTWH(1000, 0, 200, 100), photo, display), const Rect.fromLTWH(250, 0, 100, 50));
      expect(photoToDisplay(const Rect.fromLTWH(0, 0, 200, 100), photo, display), const Rect.fromLTWH(-250, 0, 100, 50));
    });

    test('scales a photo that is cropped at top and bottom', () {
      // 1080 x 1920 photo in a 270 x 360 display: scaled by 0.25, 60 cut off top and bottom.
      const photo = Size(1080, 1920);
      const display = Size(270, 360);
      expect(photoToDisplay(const Rect.fromLTWH(0, 240, 1080, 80), photo, display), const Rect.fromLTWH(0, 0, 270, 20));
    });
  });

  group('lineAt', () {
    final boxes = <Rect?>[
      const Rect.fromLTWH(20, 20, 200, 12),
      const Rect.fromLTWH(20, 34, 200, 12),
      null,
      const Rect.fromLTWH(20, 62, 120, 12),
    ];

    test('finds the line under the touch', () {
      expect(lineAt(const Offset(100, 25), boxes), 0);
      expect(lineAt(const Offset(100, 40), boxes), 1);
      expect(lineAt(const Offset(60, 70), boxes), 3);
    });

    test('takes the nearer line for a touch between two', () {
      expect(lineAt(const Offset(100, 32.5), boxes), 0);
      expect(lineAt(const Offset(100, 33.5), boxes), 1);
    });

    test('reaches a little past the end of a line, but not far', () {
      expect(lineAt(const Offset(146, 68), boxes), 3);
      expect(lineAt(const Offset(200, 68), boxes), isNull);
      expect(lineAt(const Offset(100, 110), boxes), isNull);
    });

    test('never returns a line that cannot be picked', () {
      expect(lineAt(const Offset(100, 54), [null, null]), isNull);
    });
  });
}
