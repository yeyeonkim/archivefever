import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';

import 'page_text.dart';

/// Reads the text on a photo of a book page.
abstract interface class TextReader {
  /// The lines found, in reading order, placed in the pixels of the upright photo.
  Future<List<PageLine>> read(String photoPath);

  Future<void> close();
}

/// Google ML Kit's recogniser for Korean (it reads Latin letters and digits as well). It
/// runs on the phone, so the page photo is never sent anywhere.
class MlKitTextReader implements TextReader {
  final TextRecognizer _recognizer = TextRecognizer(script: TextRecognitionScript.korean);

  @override
  Future<List<PageLine>> read(String photoPath) async {
    final found = await _recognizer.processImage(InputImage.fromFilePath(photoPath));
    return [
      // Blocks are paragraphs or columns; keeping their order keeps facing pages apart.
      for (final block in found.blocks)
        for (final line in block.lines)
          if (line.text.trim().isNotEmpty) PageLine(line.text, line.boundingBox),
    ];
  }

  @override
  Future<void> close() => _recognizer.close();
}
