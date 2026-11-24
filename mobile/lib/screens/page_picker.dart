import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../page_text.dart';

/// The page photo in the camera display with the lines of text found on it. Touching a
/// line picks it or lets it go; dragging does the same to every line from the first one
/// touched to the one under the finger.
class PagePicker extends StatefulWidget {
  const PagePicker({
    super.key,
    required this.photo,
    required this.lines,
    required this.picked,
    required this.onChanged,
    required this.onRetake,
    this.unreadable = false,
  });

  final ui.Image photo;

  /// Null while the text is still being read.
  final List<PageLine>? lines;

  /// Indices into [lines].
  final Set<int> picked;
  final ValueChanged<Set<int>> onChanged;

  /// The reader wants another photo instead of this one.
  final VoidCallback onRetake;

  /// Reading the text failed.
  final bool unreadable;

  @override
  State<PagePicker> createState() => _PagePickerState();
}

class _PagePickerState extends State<PagePicker> {
  /// The line a drag started on, whether the drag picks or lets go, and what was picked before it.
  int? _dragFrom;
  bool _dragPicks = true;
  Set<int> _before = const {};

  /// Where each line is in the display; null for lines the display's crop leaves out,
  /// which the reader cannot see and so cannot pick.
  List<Rect?> _boxes(Size display) {
    final photo = Size(widget.photo.width.toDouble(), widget.photo.height.toDouble());
    final visible = Offset.zero & display;
    return [
      for (final line in widget.lines ?? const <PageLine>[])
        switch (photoToDisplay(line.box, photo, display)) {
          final box when visible.contains(box.center) => box,
          _ => null,
        },
    ];
  }

  void _down(Offset point, List<Rect?> boxes) {
    final line = lineAt(point, boxes);
    _dragFrom = line;
    if (line == null) return;
    _before = Set.of(widget.picked);
    _dragPicks = !widget.picked.contains(line);
    _dragTo(line, boxes);
  }

  void _move(Offset point, List<Rect?> boxes) {
    if (_dragFrom == null) return;
    final line = lineAt(point, boxes);
    if (line != null) _dragTo(line, boxes);
  }

  void _dragTo(int line, List<Rect?> boxes) {
    final from = _dragFrom!;
    final next = Set.of(_before);
    for (var i = math.min(from, line); i <= math.max(from, line); i++) {
      if (boxes[i] == null) continue;
      _dragPicks ? next.add(i) : next.remove(i);
    }
    if (next.length != widget.picked.length || !next.containsAll(widget.picked)) widget.onChanged(next);
  }

  String _caption(List<Rect?> boxes) {
    if (widget.unreadable) return '글자를 읽지 못했어요';
    if (widget.lines == null) return '글자를 읽는 중…';
    if (boxes.every((box) => box == null)) return '글자를 찾지 못했어요';
    if (widget.picked.isEmpty) return '저장할 줄을 눌러 고르세요';
    return '${widget.picked.length}줄 선택';
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(builder: (context, constraints) {
      final boxes = _boxes(constraints.biggest);
      return Stack(
        fit: StackFit.expand,
        children: [
          RawImage(image: widget.photo, fit: BoxFit.cover),
          Listener(
            behavior: HitTestBehavior.opaque,
            onPointerDown: (event) => _down(event.localPosition, boxes),
            onPointerMove: (event) => _move(event.localPosition, boxes),
            onPointerUp: (_) => _dragFrom = null,
            onPointerCancel: (_) => _dragFrom = null,
            child: CustomPaint(painter: _LinesPainter(boxes, widget.picked)),
          ),
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: _CaptionBar(
              caption: _caption(boxes),
              busy: widget.lines == null && !widget.unreadable,
              onRetake: widget.onRetake,
            ),
          ),
        ],
      );
    });
  }
}

/// A thin bar along the bottom of the display: what to do next, and a way to retake the photo.
class _CaptionBar extends StatelessWidget {
  const _CaptionBar({required this.caption, required this.busy, required this.onRetake});

  final String caption;
  final bool busy;
  final VoidCallback onRetake;

  @override
  Widget build(BuildContext context) {
    const style = TextStyle(color: Colors.white, fontSize: 11.5, height: 1.2);
    const shade = Color(0xB3000000);
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // The caption lets touches through, so lines under it can still be picked.
          Expanded(
            child: IgnorePointer(
              child: ColoredBox(
                color: shade,
                child: Padding(
                  padding: const EdgeInsets.only(left: 8),
                  child: Row(
                    children: [
                      if (busy) ...[
                        const SizedBox(
                          width: 10,
                          height: 10,
                          child: CircularProgressIndicator(strokeWidth: 1.5, color: Colors.white),
                        ),
                        const SizedBox(width: 6),
                      ],
                      Expanded(child: Text(caption, style: style, maxLines: 1, overflow: TextOverflow.ellipsis)),
                    ],
                  ),
                ),
              ),
            ),
          ),
          GestureDetector(
            onTap: onRetake,
            child: ColoredBox(
              color: shade,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
                child: Text('다시 찍기', style: style.copyWith(decoration: TextDecoration.underline)),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _LinesPainter extends CustomPainter {
  _LinesPainter(this.boxes, Set<int> picked) : picked = Set.of(picked);

  final List<Rect?> boxes;
  final Set<int> picked;

  static final Paint _pickedFill = Paint()..color = const Color(0x7A3D7DFF);
  static final Paint _foundFill = Paint()..color = const Color(0x1FFFFFFF);
  static final Paint _foundEdge = Paint()
    ..color = const Color(0xB3FFFFFF)
    ..style = PaintingStyle.stroke
    ..strokeWidth = 0.8;

  @override
  void paint(Canvas canvas, Size size) {
    for (var i = 0; i < boxes.length; i++) {
      final box = boxes[i];
      if (box == null) continue;
      final shape = RRect.fromRectAndRadius(box.inflate(1), const Radius.circular(2));
      if (picked.contains(i)) {
        canvas.drawRRect(shape, _pickedFill);
      } else {
        canvas.drawRRect(shape, _foundFill);
        canvas.drawRRect(shape, _foundEdge);
      }
    }
  }

  @override
  bool shouldRepaint(_LinesPainter old) =>
      boxes.length != old.boxes.length ||
      picked.length != old.picked.length ||
      !picked.containsAll(old.picked) ||
      !_sameBoxes(old.boxes);

  bool _sameBoxes(List<Rect?> other) {
    for (var i = 0; i < boxes.length; i++) {
      if (boxes[i] != other[i]) return false;
    }
    return true;
  }
}
