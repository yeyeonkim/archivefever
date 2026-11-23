import 'dart:io';

import 'package:archive_fever/scan_service.dart';
import 'package:archive_fever/server.dart';
import 'package:archive_fever/settings.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

class MemoryPendingScan implements PendingScanStore {
  String? value;
  final List<String> saved = [];

  @override
  Future<String?> read() async => value;

  @override
  Future<void> save(String scanId) async {
    value = scanId;
    saved.add(scanId);
  }

  @override
  Future<void> clear() async => value = null;
}

void main() {
  final server = Server.parse('192.168.0.10')!;
  late Directory directory;
  late String photo;
  late MemoryPendingScan pending;
  late List<http.BaseRequest> requests;
  late List<String> bodies;

  setUp(() async {
    directory = await Directory.systemTemp.createTemp('scan-service-test');
    photo = '${directory.path}${Platform.pathSeparator}barcode.jpg';
    await File(photo).writeAsBytes([0xFF, 0xD8, 0xFF, 0xE0, 1, 2, 3]);
    pending = MemoryPendingScan();
    requests = [];
    bodies = [];
  });

  tearDown(() => directory.delete(recursive: true));

  ScanService service({required int status, required PhotoTaker takePhoto}) {
    return ScanService(
      server: server,
      pending: pending,
      takePhoto: takePhoto,
      client: MockClient.streaming((request, body) async {
        requests.add(request);
        bodies.add(String.fromCharCodes(await body.toBytes()));
        return http.StreamedResponse(Stream.value('{}'.codeUnits), status);
      }),
    );
  }

  test('a taken photo is uploaded to its scan', () async {
    final outcome = await service(status: 200, takePhoto: () async => photo).scan('scan-1');

    expect(outcome, ScanOutcome.uploaded);
    expect(requests.single.method, 'POST');
    expect(requests.single.url.toString(), 'http://192.168.0.10:8080/api/scans/scan-1/image');
    expect(requests.single.headers['content-type'], startsWith('multipart/form-data'));
    expect(bodies.single, contains('name="file"; filename="barcode.jpg"'));
    // Remembered while the camera was open, forgotten once delivered.
    expect(pending.saved, ['scan-1']);
    expect(pending.value, isNull);
  });

  test('backing out of the camera cancels the scan', () async {
    final outcome = await service(status: 200, takePhoto: () async => null).scan('scan-2');

    expect(outcome, ScanOutcome.cancelled);
    expect(requests.single.url.toString(), 'http://192.168.0.10:8080/api/scans/scan-2/cancel');
    expect(pending.value, isNull);
  });

  test('a camera that cannot be opened counts as backing out', () async {
    final outcome =
        await service(status: 200, takePhoto: () async => throw Exception('no camera')).scan('scan-3');

    expect(outcome, ScanOutcome.cancelled);
    expect(requests.single.url.path, '/api/scans/scan-3/cancel');
  });

  test('a refused upload fails and can be retried with the same photo', () async {
    var status = 500;
    final scans = ScanService(
      server: server,
      pending: pending,
      takePhoto: () async => photo,
      client: MockClient.streaming((request, body) async {
        requests.add(request);
        await body.drain<void>();
        return http.StreamedResponse(Stream.value('{}'.codeUnits), status);
      }),
    );

    expect(await scans.scan('scan-4'), ScanOutcome.failed);
    // Still remembered, so the photo is not lost if the app is closed now.
    expect(pending.value, 'scan-4');

    status = 200;
    expect(await scans.retry('scan-4'), ScanOutcome.uploaded);
    expect(requests.map((request) => request.url.path), ['/api/scans/scan-4/image', '/api/scans/scan-4/image']);
    expect(pending.value, isNull);
  });

  test('an unreachable server fails instead of throwing', () async {
    final scans = ScanService(
      server: server,
      pending: pending,
      takePhoto: () async => photo,
      client: MockClient((request) async => throw const SocketException('unreachable')),
    );

    expect(await scans.scan('scan-5'), ScanOutcome.failed);
  });

  test('retry without an undelivered photo fails', () async {
    expect(await service(status: 200, takePhoto: () async => photo).retry('scan-6'), ScanOutcome.failed);
    expect(requests, isEmpty);
  });
}
