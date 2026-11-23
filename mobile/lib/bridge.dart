import 'dart:convert';

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
}

/// Take a photo of a book's barcode and upload it to this scan.
class ScanRequest extends BridgeMessage {
  const ScanRequest(this.scanId);
  final String scanId;
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
