import 'dart:math' as math;
import 'dart:ui';

/// One line of text found on a page photo.
class PageLine {
  const PageLine(this.text, this.box);

  final String text;

  /// Where the line is on the photo, in the photo's own pixels.
  final Rect box;
}

/// Where a part of the photo appears when the photo fills [display], cropped rather than
/// stretched (the way `BoxFit.cover` draws it).
Rect photoToDisplay(Rect part, Size photo, Size display) {
  final scale = math.max(display.width / photo.width, display.height / photo.height);
  final dx = (display.width - photo.width * scale) / 2;
  final dy = (display.height - photo.height * scale) / 2;
  return Rect.fromLTRB(
    part.left * scale + dx,
    part.top * scale + dy,
    part.right * scale + dx,
    part.bottom * scale + dy,
  );
}

/// The line a touch at [point] means: the one it is on, or failing that one right next to
/// it. Lines of a full page are thinner than a fingertip. Null boxes are lines that
/// cannot be picked; the result is an index into [boxes], or null.
int? lineAt(Offset point, List<Rect?> boxes) {
  const reach = 10.0;
  int? best;
  var bestDistance = double.infinity;
  for (var i = 0; i < boxes.length; i++) {
    final box = boxes[i];
    if (box == null) continue;
    final dx = math.max(0.0, math.max(box.left - point.dx, point.dx - box.right));
    final dy = math.max(0.0, math.max(box.top - point.dy, point.dy - box.bottom));
    if (dx > reach || dy > reach) continue;
    // Among lines the touch is on or beside, the one whose middle is closest in height.
    final distance = dx + dy + (box.center.dy - point.dy).abs() / 100;
    if (distance < bestDistance) {
      best = i;
      bestDistance = distance;
    }
  }
  return best;
}

/// The page number printed on the photographed page, when one can be told apart from the
/// text: a number standing alone in the top or bottom margin, or leading or trailing a
/// short running header. Null when there is none in the photo; the reader is asked either way.
int? guessPageNumber(List<PageLine> lines) {
  if (lines.isEmpty) return null;
  final top = lines.map((line) => line.box.top).reduce(math.min);
  final bottom = lines.map((line) => line.box.bottom).reduce(math.max);
  final margin = (bottom - top) * 0.12;
  // Page numbers sit below the text more often than above it, so the bottom is tried first.
  final edges = [
    ...lines.where((line) => line.box.center.dy >= bottom - margin).toList().reversed,
    ...lines.where((line) => line.box.center.dy <= top + margin),
  ];
  for (final pattern in [_aloneNumber, _headerStart, _headerEnd]) {
    for (final line in edges) {
      final text = line.text.trim();
      if (text.length > 24) continue;
      final digits = pattern.firstMatch(text)?.group(1);
      if (digits == null || digits.startsWith('0')) continue;
      return int.parse(digits);
    }
  }
  return null;
}

final RegExp _aloneNumber = RegExp(r'^[\s\-–—·|]*(\d{1,4})[\s\-–—·|]*$');
final RegExp _headerStart = RegExp(r'^(\d{1,4})\s+\D');
final RegExp _headerEnd = RegExp(r'\D\s+(\d{1,4})$');

/// Lines of running text as one paragraph.
///
/// A Korean book breaks its lines between any two letters, so whether a line ended on a
/// word boundary is not in the photo. This guesses from the last letter: the endings and
/// particles that close most Korean words get a space after them. It is right most of the
/// time, not always, which is why the reader sees the text before it is saved.
String joinLines(Iterable<String> lines) {
  final joined = StringBuffer();
  var previous = '';
  for (final raw in lines) {
    final line = raw.trim().replaceAll(RegExp(r'\s+'), ' ');
    if (line.isEmpty) continue;
    if (previous.isNotEmpty && _spaceBetween(previous, line)) joined.write(' ');
    joined.write(line);
    previous = line;
  }
  return joined.toString();
}

bool _spaceBetween(String before, String after) {
  final last = before.substring(before.length - 1);
  final first = after.substring(0, 1);
  if (_opening.contains(last) || _closing.contains(first)) return false;
  // Punctuation, Latin letters and digits end at word boundaries.
  if (!_isHangul(last) || !_isHangul(first)) return true;
  return _wordEnds.contains(last);
}

bool _isHangul(String letter) {
  final code = letter.codeUnitAt(0);
  return code >= 0xAC00 && code <= 0xD7A3;
}

const String _opening = '([{“‘「『〈《';
const String _closing = '.,!?:;)]}”’」』〉》…';

/// Syllables that usually end a word: particles (은 는 이 가 을 를 에 의 로 ...), verb and
/// adjective endings (다 고 서 며 면 게 지 ...) and bound nouns (것 때 수 ...).
const String _wordEnds = '은는이가을를에의로와과도만고다서며면게지요죠까께나라자니던든듯뿐써터데록'
    '한할된될인운른어아해히것때수럼큼채씩쯤째';
