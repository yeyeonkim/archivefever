import 'dart:convert';

import 'package:http/http.dart' as http;

import 'server.dart';

/// Saves text collected from a page of a shelf book. Only the text the reader picked and
/// confirmed goes to the server; the page photo stays on the phone.
class PassageService {
  PassageService({required this.server, http.Client? client}) : _client = client ?? http.Client();

  static const Duration _timeout = Duration(seconds: 15);

  /// The longest text the server keeps as one passage.
  static const int maxLength = 2000;

  final Server server;
  final http.Client _client;

  /// Returns whether the passage was saved. [page] is null when the reader left it out.
  Future<bool> save({required String isbn13, required String text, int? page}) async {
    try {
      final response = await _client
          .post(
            server.resolve('/api/library/books/$isbn13/passages'),
            headers: const {'Content-Type': 'application/json; charset=utf-8'},
            body: jsonEncode({'page': page, 'text': text}),
          )
          .timeout(_timeout);
      return response.statusCode == 201;
    } on Exception {
      // Unreachable server or a timeout.
      return false;
    }
  }
}
