import 'dart:convert';
import 'dart:ui';

/// A request from the Swing UI in the web view. The page sends each one as a JSON string
/// through the `Bridge` JavaScript channel.
sealed class BridgeMessage {
  const BridgeMessage();

  /// Returns null for anything that is not a well-formed message, so a broken or
  /// unexpected page cannot make the app act.
  static BridgeMessage? parse(String raw) {
    final Object? decoded;
    try {
      decoded = jsonDecode(raw);
    } on FormatException {
      return null;
    }
    if (decoded is! Map<String, dynamic>) return null;
    switch (decoded['type']) {
      case 'scan':
        final id = decoded['scanId'];
        // The id becomes part of a request path, so only plain id characters are accepted.
        if (id is String && _scanId.hasMatch(id)) return ScanRequest(id);
        return null;
      case 'camera':
        if (decoded['visible'] == false) return const CameraRequest.hide();
        if (decoded['visible'] != true) return null;
        final x = _number(decoded['x']);
        final y = _number(decoded['y']);
        final width = _number(decoded['width']);
        final height = _number(decoded['height']);
        final radius = _number(decoded['radius']) ?? 0;
        if (x == null || y == null || width == null || height == null) return null;
        if (width <= 0 || height <= 0 || radius < 0) return null;
        return CameraRequest.show(Rect.fromLTWH(x, y, width, height), radius, moved: decoded['moved'] == true);
      case 'capture':
        return const CaptureRequest();
      case 'capturePage':
        return const PageCaptureRequest();
      case 'savePassage':
        final request = decoded['request'];
        final isbn13 = decoded['isbn13'];
        if (request is! int || request < 1 || request > _maxRequest) return null;
        // The ISBN becomes part of a request path.
        if (isbn13 is! String || !_isbn13.hasMatch(isbn13)) return null;
        return SavePassageRequest(request, isbn13);
      case 'openUrl':
        final url = decoded['url'];
        final uri = url is String ? Uri.tryParse(url) : null;
        if (uri != null && uri.host.isNotEmpty && (uri.scheme == 'https' || uri.scheme == 'http')) {
          return OpenUrlRequest(uri);
        }
        return null;
      case 'ready':
        return const PageReady();
      default:
        return null;
    }
  }

  static final RegExp _scanId = RegExp(r'^[A-Za-z0-9-]{1,64}$');
  static final RegExp _isbn13 = RegExp(r'^\d{13}$');

  /// Request numbers travel back to the page packed into one small integer.
  static const int _maxRequest = 1 << 26;

  /// A finite number of a size that can be a position on a screen, or null.
  static double? _number(Object? value) {
    if (value is! num || !value.isFinite || value.abs() > 100000) return null;
    return value.toDouble();
  }
}

/// Upload a photo of a book's barcode to this scan: the one just taken on a
/// [CaptureRequest], or else one the person is asked for.
class ScanRequest extends BridgeMessage {
  const ScanRequest(this.scanId);
  final String scanId;
}

/// The Swing camera screen came into view or left it. While in view, the live camera
/// preview belongs over [rect], the display of the camera drawn on that screen.
class CameraRequest extends BridgeMessage {
  const CameraRequest.show(Rect this.rect, this.radius, {this.moved = false});
  const CameraRequest.hide()
      : rect = null,
        radius = 0,
        moved = false;

  /// Where the preview goes, in page coordinates; null when it should not be shown.
  final Rect? rect;

  /// Corner rounding of the display.
  final double radius;

  /// The display already in view is drawn at a new place (the page was laid out again).
  /// Whatever it is showing, live preview or a photo under review, stays as it is.
  final bool moved;
}

/// Take the photo now: the scan button was just pressed.
class CaptureRequest extends BridgeMessage {
  const CaptureRequest();
}

/// Photograph the book page in the preview now, and let the reader pick lines of its text.
class PageCaptureRequest extends BridgeMessage {
  const PageCaptureRequest();
}

/// Save the lines the reader picked on the page photo as a passage of this shelf book.
/// The page waits for an answer carrying [request]: `window.hostReply(request, outcome)`.
class SavePassageRequest extends BridgeMessage {
  const SavePassageRequest(this.request, this.isbn13);
  final int request;
  final String isbn13;
}

/// How a [SavePassageRequest] ended, as the number the page is answered with.
enum PassageOutcome {
  saved,

  /// No lines were picked, so there was nothing to save.
  nothingPicked,

  /// The reader backed out when shown the text.
  cancelled,
  failed,
}

/// Open a web page outside the app.
class OpenUrlRequest extends BridgeMessage {
  const OpenUrlRequest(this.url);
  final Uri url;
}

/// The Swing UI has started.
class PageReady extends BridgeMessage {
  const PageReady();
}
