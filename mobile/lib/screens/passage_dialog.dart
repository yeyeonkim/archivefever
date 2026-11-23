import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// The longest text the server keeps as one passage.
const int maxPassageLength = 2000;

/// Saves the text as a passage; returns whether it was saved.
typedef PassageSaver = Future<bool> Function(String text, int? page);

/// Shows the text read off the page photo and the page number found on it, for the reader
/// to correct, and saves them. Returns true once saved, false if the reader backs out.
Future<bool> showPassageDialog(
  BuildContext context, {
  required String text,
  required int? page,
  required PassageSaver save,
}) async {
  final saved = await showDialog<bool>(
    context: context,
    // A stray touch outside must not throw away corrections made to the text.
    barrierDismissible: false,
    builder: (_) => _PassageDialog(text: text, page: page, save: save),
  );
  return saved ?? false;
}

class _PassageDialog extends StatefulWidget {
  const _PassageDialog({required this.text, required this.page, required this.save});

  final String text;
  final int? page;
  final PassageSaver save;

  @override
  State<_PassageDialog> createState() => _PassageDialogState();
}

class _PassageDialogState extends State<_PassageDialog> {
  late final TextEditingController _text = TextEditingController(text: widget.text);
  late final TextEditingController _page = TextEditingController(text: widget.page?.toString() ?? '');
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _text.dispose();
    _page.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final text = _text.text.trim();
    final pageText = _page.text.trim();
    final page = pageText.isEmpty ? null : int.tryParse(pageText);
    if (text.isEmpty) {
      setState(() => _error = '저장할 문장이 없어요.');
      return;
    }
    if (pageText.isNotEmpty && (page == null || page < 1)) {
      setState(() => _error = '쪽수는 1 이상의 숫자로 적어 주세요.');
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    final saved = await widget.save(text, page);
    if (!mounted) return;
    if (saved) {
      Navigator.of(context).pop(true);
    } else {
      // The dialog stays open, so nothing typed is lost and save can be pressed again.
      setState(() {
        _saving = false;
        _error = '저장하지 못했어요. 다시 시도해 주세요.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final error = _error;
    return AlertDialog(
      scrollable: true,
      title: const Text('문장 저장'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextField(
            key: const Key('passage-text'),
            controller: _text,
            enabled: !_saving,
            minLines: 3,
            maxLines: 7,
            maxLength: maxPassageLength,
            decoration: const InputDecoration(
              labelText: '문장',
              helperText: '잘못 읽은 글자나 띄어쓰기는 고쳐 주세요.',
              // The character counter shares the line; on a phone the hint needs a second one.
              helperMaxLines: 2,
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            key: const Key('passage-page'),
            controller: _page,
            enabled: !_saving,
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(4)],
            decoration: InputDecoration(
              labelText: '쪽수',
              helperText: widget.page == null ? '모르면 비워 두어도 돼요.' : '사진에서 읽은 쪽수예요. 맞는지 확인해 주세요.',
              helperMaxLines: 2,
              border: const OutlineInputBorder(),
            ),
          ),
          if (error != null) ...[
            const SizedBox(height: 12),
            Text(error, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
        ],
      ),
      actions: [
        TextButton(
          onPressed: _saving ? null : () => Navigator.of(context).pop(false),
          child: const Text('취소'),
        ),
        FilledButton(
          onPressed: _saving ? null : _save,
          child: _saving
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('저장'),
        ),
      ],
    );
  }
}
