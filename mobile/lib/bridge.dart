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
        return CameraRequest.show(Rect.fromLTWH(x, y, width, height), radius);
      case 'capture':
        return const CaptureRequest();
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
  const CameraRequest.show(Rect this.rect, this.radius);
  const CameraRequest.hide()
      : rect = null,
        radius = 0;

  /// Where the preview goes, in page coordinates; null when it should not be shown.
  final Rect? rect;

  /// Corner rounding of the display.
  final double radius;
}

/// Take the photo now: the scan button was just pressed.
class CaptureRequest extends BridgeMessage {
  const CaptureRequest();
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
