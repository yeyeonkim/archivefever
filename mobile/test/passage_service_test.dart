import 'dart:convert';

import 'package:archive_fever/passage_service.dart';
import 'package:archive_fever/server.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  final server = Server.parse('192.168.0.10')!;

  test('posts the text and page as JSON to the book', () async {
    late http.Request sent;
    final service = PassageService(
      server: server,
      client: MockClient((request) async {
        sent = request;
        return http.Response('{"id":1,"page":127,"text":"..."}', 201);
      }),
    );

    final saved = await service.save(isbn13: '9788936434120', text: '어둠 속에서 "손을" 뻗었다.', page: 127);

    expect(saved, isTrue);
    expect(sent.method, 'POST');
    expect(sent.url, Uri.parse('http://192.168.0.10:8080/api/library/books/9788936434120/passages'));
    expect(sent.headers['content-type'], startsWith('application/json'));
    expect(jsonDecode(utf8.decode(sent.bodyBytes)), {'page': 127, 'text': '어둠 속에서 "손을" 뻗었다.'});
  });

  test('sends no page when the reader left it out', () async {
    late http.Request sent;
    final service = PassageService(
      server: server,
      client: MockClient((request) async {
        sent = request;
        return http.Response('{}', 201);
      }),
    );

    expect(await service.save(isbn13: '9788936434120', text: '문장.'), isTrue);
    expect(jsonDecode(utf8.decode(sent.bodyBytes)), {'page': null, 'text': '문장.'});
  });

  test('reports a refusal or an unreachable server as not saved', () async {
    final refused = PassageService(server: server, client: MockClient((_) async => http.Response('', 404)));
    final unreachable = PassageService(
      server: server,
      client: MockClient((_) async => throw http.ClientException('connection refused')),
    );

    expect(await refused.save(isbn13: '9788936434120', text: '문장.', page: 1), isFalse);
    expect(await unreachable.save(isbn13: '9788936434120', text: '문장.', page: 1), isFalse);
  });
}
