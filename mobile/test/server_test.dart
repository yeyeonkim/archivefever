import 'package:archive_fever/server.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Server.parse', () {
    test('a bare host gets http and the default port', () {
      expect(Server.parse('192.168.0.10').toString(), 'http://192.168.0.10:8080');
      expect(Server.parse('  192.168.0.10  ').toString(), 'http://192.168.0.10:8080');
    });

    test('a given port and scheme are kept', () {
      expect(Server.parse('192.168.0.10:9000').toString(), 'http://192.168.0.10:9000');
      expect(Server.parse('http://laptop.local:8080').toString(), 'http://laptop.local:8080');
      expect(Server.parse('https://example.org').toString(), 'https://example.org');
    });

    test('a pasted page address is reduced to the server', () {
      expect(Server.parse('http://192.168.0.10:8080/?timer').toString(), 'http://192.168.0.10:8080');
    });

    test('text that is not an address is refused', () {
      expect(Server.parse(''), isNull);
      expect(Server.parse('   '), isNull);
      expect(Server.parse('ftp://192.168.0.10'), isNull);
      expect(Server.parse('http://'), isNull);
    });
  });

  test('resolve builds request addresses', () {
    final server = Server.parse('192.168.0.10')!;

    expect(server.resolve('/api/scans/abc/image').toString(), 'http://192.168.0.10:8080/api/scans/abc/image');
  });

  test('owns tells the server\'s pages from outside links', () {
    final server = Server.parse('192.168.0.10')!;

    expect(server.owns(Uri.parse('http://192.168.0.10:8080/')), isTrue);
    expect(server.owns(Uri.parse('http://192.168.0.10:8080/swing/app.jar')), isTrue);
    expect(server.owns(Uri.parse('http://192.168.0.10:9000/')), isFalse);
    expect(server.owns(Uri.parse('https://www.yes24.com/')), isFalse);
  });
}
