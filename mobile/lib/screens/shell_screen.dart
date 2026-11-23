import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../bridge.dart';
import '../exit_guard.dart';
import '../scan_service.dart';
import '../server.dart';
import '../settings.dart';
import 'server_setup_screen.dart';

/// Shows the Swing UI served by the backend, and does for it what a web page cannot:
/// show the camera, take photos and open outside links.
class ShellScreen extends StatefulWidget {
  const ShellScreen({super.key, required this.server, required this.settings});

  final Server server;
  final Settings settings;

  @override
  State<ShellScreen> createState() => _ShellScreenState();
}

class _ShellScreenState extends State<ShellScreen> with WidgetsBindingObserver {
  static const Color _backdrop = Color(0xFF2B211A);

  late final ScanService _scans = ScanService(server: widget.server, pending: widget.settings);
  final ExitGuard _exitGuard = ExitGuard();
  late final WebViewController _web;
  bool _uploading = false;
  String? _loadError;

  /// Where the Swing camera screen wants the live preview; null while that screen is not in view.
  CameraRequest? _cameraPlace;

  /// The running camera, once it has started.
  CameraController? _camera;

  /// Bumped whenever the camera is started or stopped, so a start that finishes late is dropped.
  int _cameraRun = 0;

  /// The photo taken when scan was pressed, waiting for the scan it belongs to.
  Future<String?>? _shot;

  /// After a shot the Swing UI shows its result popup where the preview was, so the
  /// preview steps aside until the camera screen asks for it again.
  bool _reviewing = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _web = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(_backdrop)
      ..addJavaScriptChannel('Bridge', onMessageReceived: (message) => _handle(message.message))
      ..setNavigationDelegate(NavigationDelegate(
        onNavigationRequest: (request) {
          final target = Uri.tryParse(request.url);
          if (target != null && widget.server.owns(target)) return NavigationDecision.navigate;
          // Frames inside the page (the Java runtime loads some) are the page's own business.
          if (!request.isMainFrame) return NavigationDecision.navigate;
          // Anything else is an outside link and belongs in the browser.
          if (target != null) _open(target);
          return NavigationDecision.prevent;
        },
        onWebResourceError: (error) {
          if (error.isForMainFrame ?? false) setState(() => _loadError = error.description);
        },
      ))
      ..loadRequest(widget.server.base);
    _recover();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _camera?.dispose();
    super.dispose();
  }

  /// The camera must be released while the app is in the background.
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.inactive || state == AppLifecycleState.paused) {
      _stopCamera();
    } else if (state == AppLifecycleState.resumed && _cameraPlace != null && !_reviewing) {
      _startCamera();
    }
  }

  /// A photo may have been taken just before Android stopped the app for the camera.
  Future<void> _recover() async {
    final outcome = await _scans.recoverLostPhoto();
    if (mounted && outcome == ScanOutcome.failed) _showMessage('사진을 올리지 못했어요.');
  }

  void _handle(String raw) {
    switch (BridgeMessage.parse(raw)) {
      case final CameraRequest request:
        _placeCamera(request);
      case CaptureRequest():
        _capture();
      case ScanRequest(:final scanId):
        _scan(scanId);
      case OpenUrlRequest(:final url):
        _open(url);
      case PageReady():
      case null:
        break;
    }
  }

  // --- Live camera preview ----------------------------------------------------------

  void _placeCamera(CameraRequest request) {
    if (request.rect == null) {
      setState(() => _cameraPlace = null);
      _stopCamera();
      return;
    }
    // A shot from the last visit (a sentence page, say) must not be sent with the next scan.
    _shot = null;
    setState(() {
      _cameraPlace = request;
      _reviewing = false;
    });
    final camera = _camera;
    if (camera == null) {
      _startCamera();
    } else if (camera.value.isPreviewPaused) {
      camera.resumePreview().catchError((Object _) {});
    }
  }

  Future<void> _startCamera() async {
    final run = ++_cameraRun;
    try {
      final cameras = await availableCameras();
      if (cameras.isEmpty) return;
      final back = cameras.firstWhere(
        (camera) => camera.lensDirection == CameraLensDirection.back,
        orElse: () => cameras.first,
      );
      // Enough detail for a barcode held at reading distance.
      final camera = CameraController(back, ResolutionPreset.veryHigh, enableAudio: false);
      await camera.initialize();
      if (!mounted || run != _cameraRun || _cameraPlace == null) {
        await camera.dispose();
        return;
      }
      setState(() => _camera = camera);
    } on CameraException {
      // No camera, or permission refused. The scan button then opens the system camera.
    }
  }

  void _stopCamera() {
    _cameraRun++;
    final camera = _camera;
    _shot = null;
    if (camera == null) return;
    if (mounted) setState(() => _camera = null);
    camera.dispose();
  }

  /// Scan was pressed: take the picture at once, and hold the frame on screen until the
  /// Swing UI has its result popup up.
  void _capture() {
    final camera = _camera;
    if (camera == null || !camera.value.isInitialized || camera.value.isTakingPicture) return;
    _shot = () async {
      try {
        final photo = await camera.takePicture();
        await camera.pausePreview().catchError((Object _) {});
        return photo.path;
      } on CameraException {
        return null;
      }
    }();
  }

  // --- Scanning -----------------------------------------------------------------------

  Future<void> _scan(String scanId) async {
    final shot = _shot;
    _shot = null;
    if (shot != null) {
      // The picture is already taken; the Swing popup reports what comes of it.
      setState(() => _reviewing = true);
      final outcome = await _scans.scan(scanId, takePhoto: () => shot);
      if (mounted && outcome == ScanOutcome.failed) _offerRetry(scanId);
      return;
    }
    if (_uploading) return;
    setState(() => _uploading = true);
    final outcome = await _scans.scan(scanId);
    if (!mounted) return;
    setState(() => _uploading = false);
    if (outcome == ScanOutcome.failed) _offerRetry(scanId);
  }

  void _offerRetry(String scanId) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: const Text('사진을 올리지 못했어요.'),
      duration: const Duration(seconds: 20),
      action: SnackBarAction(
        label: '다시 시도',
        onPressed: () async {
          setState(() => _uploading = true);
          final outcome = await _scans.retry(scanId);
          if (!mounted) return;
          setState(() => _uploading = false);
          if (outcome == ScanOutcome.failed) _offerRetry(scanId);
        },
      ),
    ));
  }

  Future<void> _open(Uri url) async {
    final opened = await launchUrl(url, mode: LaunchMode.externalApplication);
    if (!opened && mounted) _showMessage('링크를 열 수 없어요.');
  }

  /// The Back button. Leaving would discard the running Swing UI, so the first press only
  /// closes whatever popup the UI has open; a second press right after it leaves.
  void _back() {
    if (_exitGuard.shouldExit()) {
      SystemNavigator.pop();
      return;
    }
    // Defined by the host page; absent while the page is still loading.
    _web.runJavaScript('window.hostBack && window.hostBack()').catchError((Object _) {});
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(const SnackBar(
        content: Text('한 번 더 누르면 앱을 종료합니다.'),
        duration: Duration(seconds: 2),
      ));
  }

  void _showMessage(String text) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  void _reload() {
    setState(() => _loadError = null);
    _web.loadRequest(widget.server.base);
  }

  void _changeServer() {
    Navigator.of(context).pushReplacement(MaterialPageRoute<void>(
      builder: (_) => ServerSetupScreen(settings: widget.settings),
    ));
  }

  @override
  Widget build(BuildContext context) {
    final camera = _camera;
    final place = _cameraPlace;
    final showPreview = camera != null && place != null && !_reviewing && _loadError == null;
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop) _back();
      },
      child: Scaffold(
        backgroundColor: _backdrop,
        body: SafeArea(
          child: Stack(
            children: [
              WebViewWidget(controller: _web),
              // The page and this stack share one origin and one unit, so the rectangle
              // the Swing UI reports is where its camera display is on screen.
              if (showPreview)
                Positioned.fromRect(
                  rect: place.rect!,
                  child: IgnorePointer(
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(place.radius),
                      child: _CoverPreview(camera: camera),
                    ),
                  ),
                ),
              if (_loadError != null)
                _LoadError(server: widget.server, onRetry: _reload, onChangeServer: _changeServer),
              if (_uploading)
                const ColoredBox(
                  color: Color(0x99000000),
                  child: Center(child: CircularProgressIndicator()),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

/// The camera picture, filling its box and cropped rather than stretched.
class _CoverPreview extends StatelessWidget {
  const _CoverPreview({required this.camera});

  final CameraController camera;

  @override
  Widget build(BuildContext context) {
    // The sensor reports its size lying on its side; the app is held upright.
    final sensor = camera.value.previewSize;
    if (sensor == null) return const ColoredBox(color: Colors.black);
    return FittedBox(
      fit: BoxFit.cover,
      clipBehavior: Clip.hardEdge,
      child: SizedBox(width: sensor.height, height: sensor.width, child: CameraPreview(camera)),
    );
  }
}

class _LoadError extends StatelessWidget {
  const _LoadError({required this.server, required this.onRetry, required this.onChangeServer});

  final Server server;
  final VoidCallback onRetry;
  final VoidCallback onChangeServer;

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: Theme.of(context).colorScheme.surface,
      child: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('$server 에 연결하지 못했어요.', textAlign: TextAlign.center),
              const SizedBox(height: 16),
              FilledButton(onPressed: onRetry, child: const Text('다시 시도')),
              TextButton(onPressed: onChangeServer, child: const Text('서버 주소 변경')),
            ],
          ),
        ),
      ),
    );
  }
}
