/// The Archive Fever server this app talks to: the laptop running the Spring backend.
class Server {
  const Server(this.base);

  /// Address without a path, for example `http://192.168.0.10:8080`.
  final Uri base;

  static const int defaultPort = 8080;

  /// Turns what a person types into a server address.
  ///
  /// Accepts a bare host (`192.168.0.10`), host and port, or a full URL. Returns null
  /// when the text cannot be an address.
  static Server? parse(String input) {
    var text = input.trim();
    if (text.isEmpty) return null;
    if (!text.contains('://')) text = 'http://$text';
    final uri = Uri.tryParse(text);
    if (uri == null || uri.host.isEmpty) return null;
    if (uri.scheme != 'http' && uri.scheme != 'https') return null;
    return Server(Uri(
      scheme: uri.scheme,
      host: uri.host,
      port: uri.hasPort ? uri.port : (uri.scheme == 'http' ? defaultPort : null),
    ));
  }

  Uri resolve(String path) => base.replace(path: path);

  /// Whether [uri] is a page of this server, as opposed to an outside link.
  bool owns(Uri uri) =>
      uri.scheme == base.scheme && uri.host == base.host && uri.port == base.port;

  @override
  String toString() => base.toString();
}
