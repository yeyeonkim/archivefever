import 'dart:async';

import 'package:http/http.dart' as http;
import 'package:image_picker/image_picker.dart';

import 'server.dart';
import 'settings.dart';

/// Returns the path of a newly taken photo, or null when the person backed out.
typedef PhotoTaker = Future<String?> Function();

enum ScanOutcome {
  /// The photo reached the server. What the server found is shown by the Swing UI.
  uploaded,

  /// No photo was taken; the scan was cancelled on the server.
  cancelled,

  /// A photo was taken but could not be delivered. It can be sent again.
  failed,
}

/// The phone's half of book registration: take the barcode photo the Swing UI asked for
/// and deliver it to the scan it created.
class ScanService {
  ScanService({
    required this.server,
    required this.pending,
    http.Client? client,
    PhotoTaker? takePhoto,
  })  : _client = client ?? http.Client(),
        _takePhoto = takePhoto ?? _cameraPhoto;

  static const Duration _timeout = Duration(seconds: 40);

  final Server server;
  final PendingScanStore pending;
  final http.Client _client;
  final PhotoTaker _takePhoto;

  /// Path of the last photo that failed to upload, kept for [retry].
  String? _undelivered;

  Future<ScanOutcome> scan(String scanId) async {
    await pending.save(scanId);
    String? photo;
    try {
      photo = await _takePhoto();
    } catch (_) {
      // No camera, or permission refused: the same as backing out.
      photo = null;
    }
    if (photo == null) {
      await _cancel(scanId);
      await pending.clear();
      return ScanOutcome.cancelled;
    }
    return _deliver(scanId, photo);
  }

  /// Sends again the photo that [scan] could not deliver.
  Future<ScanOutcome> retry(String scanId) async {
    final photo = _undelivered;
    if (photo == null) return ScanOutcome.failed;
    return _deliver(scanId, photo);
  }

  /// Uploads a photo taken just before Android stopped the app, if there is one.
  Future<ScanOutcome?> recoverLostPhoto() async {
    final scanId = await pending.read();
    if (scanId == null) return null;
    final lost = await ImagePicker().retrieveLostData();
    final photo = lost.file?.path;
    if (photo == null) {
      await pending.clear();
      return null;
    }
    return _deliver(scanId, photo);
  }

  Future<ScanOutcome> _deliver(String scanId, String photo) async {
    try {
      final request = http.MultipartRequest('POST', server.resolve('/api/scans/$scanId/image'))
        ..files.add(await http.MultipartFile.fromPath('file', photo));
      final response = await _client.send(request).timeout(_timeout);
      await response.stream.drain<void>();
      if (response.statusCode == 200) {
        _undelivered = null;
        await pending.clear();
        return ScanOutcome.uploaded;
      }
    } on Exception {
      // Reported as a failure below.
    }
    _undelivered = photo;
    return ScanOutcome.failed;
  }

  Future<void> _cancel(String scanId) async {
    try {
      await _client.post(server.resolve('/api/scans/$scanId/cancel')).timeout(const Duration(seconds: 10));
    } on Exception {
      // The scan expires on the server by itself.
    }
  }

  static Future<String?> _cameraPhoto() async {
    final photo = await ImagePicker().pickImage(
      source: ImageSource.camera,
      // Plenty for reading a barcode, and a fraction of a full camera photo to upload.
      maxWidth: 1600,
      maxHeight: 1600,
      imageQuality: 88,
    );
    return photo?.path;
  }
}
