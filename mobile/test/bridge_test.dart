import 'dart:ui';

import 'package:archive_fever/bridge.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('BridgeMessage.parse', () {
    test('reads a scan request', () {
      final message = BridgeMessage.parse('{"type":"scan","scanId":"08290275-1897-4f85-bd5a-4e51c624c2c1"}');

      expect(message, isA<ScanRequest>());
      expect((message as ScanRequest).scanId, '08290275-1897-4f85-bd5a-4e51c624c2c1');
    });

    test('reads a link request', () {
      final message = BridgeMessage.parse('{"type":"openUrl","url":"https://www.yes24.com/Product/Goods/1"}');

      expect((message as OpenUrlRequest).url, Uri.parse('https://www.yes24.com/Product/Goods/1'));
    });

    test('reads where the camera preview belongs', () {
      final message = BridgeMessage.parse(
          '{"type":"camera","visible":true,"x":45.37,"y":123.01,"width":237.29,"height":321.91,"radius":6.98}');

      final request = message as CameraRequest;
      expect(request.rect, const Rect.fromLTWH(45.37, 123.01, 237.29, 321.91));
      expect(request.radius, 6.98);
    });

    test('reads that the camera screen has gone', () {
      final message = BridgeMessage.parse('{"type":"camera","visible":false}');

      expect((message as CameraRequest).rect, isNull);
    });

    test('rejects a camera place that cannot be on a screen', () {
      expect(BridgeMessage.parse('{"type":"camera","visible":true,"x":0,"y":0,"width":0,"height":100}'), isNull);
      expect(BridgeMessage.parse('{"type":"camera","visible":true,"x":0,"y":0,"width":-5,"height":100}'), isNull);
      expect(BridgeMessage.parse('{"type":"camera","visible":true,"x":"0","y":0,"width":10,"height":10}'), isNull);
      expect(BridgeMessage.parse('{"type":"camera","visible":true,"x":0,"y":0,"width":1e9,"height":10}'), isNull);
      expect(BridgeMessage.parse('{"type":"camera","visible":true}'), isNull);
      expect(BridgeMessage.parse('{"type":"camera"}'), isNull);
    });

    test('reads the request to take the photo now', () {
      expect(BridgeMessage.parse('{"type":"capture"}'), isA<CaptureRequest>());
    });

    test('tells a display that only moved from one that came into view', () {
      const place = '"x":45,"y":123,"width":237,"height":322,"radius":7';

      expect((BridgeMessage.parse('{"type":"camera","visible":true,$place}') as CameraRequest).moved, isFalse);
      expect((BridgeMessage.parse('{"type":"camera","visible":true,$place,"moved":false}') as CameraRequest).moved, isFalse);
      expect((BridgeMessage.parse('{"type":"camera","visible":true,$place,"moved":true}') as CameraRequest).moved, isTrue);
    });

    test('reads the request to photograph a book page', () {
      expect(BridgeMessage.parse('{"type":"capturePage"}'), isA<PageCaptureRequest>());
    });

    test('reads the request to save the picked lines', () {
      final message = BridgeMessage.parse('{"type":"savePassage","request":3,"isbn13":"9788936434120"}');

      final request = message as SavePassageRequest;
      expect(request.request, 3);
      expect(request.isbn13, '9788936434120');
    });

    test('rejects save requests it could not answer or that could change the request path', () {
      expect(BridgeMessage.parse('{"type":"savePassage","isbn13":"9788936434120"}'), isNull);
      expect(BridgeMessage.parse('{"type":"savePassage","request":0,"isbn13":"9788936434120"}'), isNull);
      expect(BridgeMessage.parse('{"type":"savePassage","request":1.5,"isbn13":"9788936434120"}'), isNull);
      expect(BridgeMessage.parse('{"type":"savePassage","request":"3","isbn13":"9788936434120"}'), isNull);
      expect(BridgeMessage.parse('{"type":"savePassage","request":99999999999,"isbn13":"9788936434120"}'), isNull);
      expect(BridgeMessage.parse('{"type":"savePassage","request":3}'), isNull);
      expect(BridgeMessage.parse('{"type":"savePassage","request":3,"isbn13":"978893643412"}'), isNull);
      expect(BridgeMessage.parse('{"type":"savePassage","request":3,"isbn13":"../../scans/x"}'), isNull);
      expect(BridgeMessage.parse('{"type":"savePassage","request":3,"isbn13":9788936434120}'), isNull);
    });

    test('reads the page saying whether it saved the passage', () {
      final saved = BridgeMessage.parse('{"type":"passageSaved","request":3,"saved":true}') as PassageSaved;
      final refused = BridgeMessage.parse('{"type":"passageSaved","request":4,"saved":false}') as PassageSaved;

      expect((saved.request, saved.saved), (3, true));
      expect((refused.request, refused.saved), (4, false));
      expect(BridgeMessage.parse('{"type":"passageSaved","request":3}'), isNull);
      expect(BridgeMessage.parse('{"type":"passageSaved","request":3,"saved":"yes"}'), isNull);
      expect(BridgeMessage.parse('{"type":"passageSaved","request":0,"saved":true}'), isNull);
      expect(BridgeMessage.parse('{"type":"passageSaved","saved":true}'), isNull);
    });

    test('answers the page with the outcome numbers the Swing UI expects', () {
      // The order of archivefever.host.HostBridge.PassageResult.
      expect(PassageOutcome.values.map((outcome) => outcome.name), ['saved', 'nothingPicked', 'cancelled', 'failed']);
    });

    test('reads the ready signal', () {
      expect(BridgeMessage.parse('{"type":"ready"}'), isA<PageReady>());
      expect(BridgeMessage.parse('{"type":"signedIn"}'), isA<SignedIn>());
    });

    test('rejects scan ids that could change the request path', () {
      expect(BridgeMessage.parse('{"type":"scan","scanId":"../library"}'), isNull);
      expect(BridgeMessage.parse('{"type":"scan","scanId":"a/b"}'), isNull);
      expect(BridgeMessage.parse('{"type":"scan","scanId":""}'), isNull);
      expect(BridgeMessage.parse('{"type":"scan","scanId":42}'), isNull);
      expect(BridgeMessage.parse('{"type":"scan"}'), isNull);
    });

    test('rejects links that are not web pages', () {
      expect(BridgeMessage.parse('{"type":"openUrl","url":"intent://evil"}'), isNull);
      expect(BridgeMessage.parse('{"type":"openUrl","url":"file:///sdcard/x"}'), isNull);
      expect(BridgeMessage.parse('{"type":"openUrl","url":"javascript:alert(1)"}'), isNull);
      expect(BridgeMessage.parse('{"type":"openUrl"}'), isNull);
    });

    test('ignores anything that is not a known message', () {
      expect(BridgeMessage.parse('not json'), isNull);
      expect(BridgeMessage.parse('[1,2]'), isNull);
      expect(BridgeMessage.parse('"scan"'), isNull);
      expect(BridgeMessage.parse('{"type":"format-disk"}'), isNull);
      expect(BridgeMessage.parse('{}'), isNull);
    });
  });
}
