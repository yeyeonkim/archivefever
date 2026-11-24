import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../bridge.dart';
import '../exit_guard.dart';
import '../page_text.dart';
import '../scan_service.dart';
import '../server.dart';
import '../settings.dart';
import '../text_reader.dart';
import 'page_picker.dart';
import 'passage_dialog.dart';
import 'server_setup_screen.dart';

/// Shows the Swing UI served by the backend, and does for it what a web page cannot:
/// show the camera, take photos, read the text on a page photo and open outside links.
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
  final TextReader _reader = MlKitTextReader();

  /// Passages handed to the page to save, waiting for its answer, by request number.
  final Map<int, Completer<bool>> _saving = {};
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

  /// The page photo taken on the sentence screen, shown in place of the live preview for
  /// the reader to pick lines on; null on the book screen and before the shot.
  _PageReview? _page;

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
          // Signing in with Kakao has to happen in this web view: the session cookie the
          // server sets on the way back must land here, not in the phone's browser.
          if (target != null && _isKakaoSignIn(target)) return NavigationDecision.navigate;
          // Kakao's page may also try to open the KakaoTalk app (intent: or kakaotalk: links).
          // That is not wired up; staying put leaves its sign-in form to use.
          if (target == null || (target.scheme != 'https' && target.scheme != 'http')) {
            return NavigationDecision.prevent;
          }
          // Anything else is an outside link and belongs in the browser.
          _open(target);
          return NavigationDecision.prevent;
        },
        onWebResourceError: (error) {
          if (error.isForMainFrame ?? false) setState(() => _loadError = error.description);
        },
      ));
    _load();
    _recover();
  }

  /// Opens the server's page.
  ///
  /// A free ngrok address answers every request from a browser, the page's own requests
  /// for the Swing files and the API included, with a notice page until the visitor presses
  /// "Visit Site", which sets a cookie. The app sets that cookie itself first: whoever uses
  /// the app chose this server by typing its address. If ngrok ever changes the cookie,
  /// the notice shows and pressing its button still works.
  Future<void> _load() async {
    if (widget.server.isFreeNgrok) {
      final host = widget.server.base.host;
      try {
        await WebViewCookieManager().setCookie(WebViewCookie(name: 'abuse_interstitial', value: host, domain: host));
      } on Exception {
        // The notice page will show instead.
      }
    }
    if (mounted) await _web.loadRequest(widget.server.base);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _camera?.dispose();
    _page?.photo?.dispose();
    _reader.close();
    super.dispose();
  }

  /// The camera must be released while the app is in the background.
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.inactive || state == AppLifecycleState.paused) {
      _stopCamera();
    } else if (state == AppLifecycleState.resumed && _cameraPlace != null && !_reviewing && _page == null) {
      // A page under review needs no camera; it starts again if the reader retakes the photo.
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
      case PageCaptureRequest():
        _capturePage();
      case SavePassageRequest(:final request, :final isbn13):
        _savePassage(request, isbn13);
      case PassageSaved(:final request, :final saved):
        _saving.remove(request)?.complete(saved);
      case ScanRequest(:final scanId):
        _scan(scanId);
      case OpenUrlRequest(:final url):
        _open(url);
      case SignedIn():
      case PageReady():
        _hideKeyboard();
      case null:
        break;
    }
  }

  /// The page's sign-in form has gone, or the Swing UI is up, which has no use for the
  /// keyboard at that moment. A web view field losing focus does not always take the
  /// keyboard down with it, and it would cover half the screen.
  void _hideKeyboard() {
    FocusManager.instance.primaryFocus?.unfocus();
    SystemChannels.textInput.invokeMethod<void>('TextInput.hide');
  }

  // --- Live camera preview ----------------------------------------------------------

  void _placeCamera(CameraRequest request) {
    if (request.rect == null) {
      _dropPage();
      setState(() => _cameraPlace = null);
      _stopCamera();
      return;
    }
    if (request.moved && _cameraPlace != null) {
      // Same screen, laid out again (the keyboard opening does that): only the place changes.
      setState(() => _cameraPlace = request);
      return;
    }
    // A shot from the last visit must not be sent with the next scan, and a page under
    // review gives way to the live preview: this is how its photo is retaken.
    _shot = null;
    _dropPage();
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

  // --- Collecting sentences ---------------------------------------------------------

  /// Scan was pressed on the sentence screen: photograph the page, show the photo where
  /// the preview was and read its text, all on the phone.
  Future<void> _capturePage() async {
    final camera = _camera;
    if (camera == null || !camera.value.isInitialized || camera.value.isTakingPicture) return;
    final review = _PageReview();
    _dropPage();
    setState(() => _page = review);
    try {
      final shot = await camera.takePicture();
      await camera.pausePreview().catchError((Object _) {});
      final file = File(shot.path);
      try {
        final photo = await decodeImageFromList(await file.readAsBytes());
        if (!mounted || _page != review) {
          photo.dispose();
          return;
        }
        setState(() => review.photo = photo);
        final lines = await _reader.read(shot.path);
        if (!mounted || _page != review) return;
        setState(() => review.lines = lines);
      } finally {
        // The photo was only needed to read the text and to show it; neither needs the file.
        file.delete().ignore();
      }
    } on Exception {
      if (mounted && _page == review) setState(() => review.unreadable = true);
    }
  }

  void _dropPage() {
    final photo = _page?.photo;
    if (_page == null) return;
    setState(() => _page = null);
    // Released once the frame that stops drawing it is done.
    if (photo != null) WidgetsBinding.instance.addPostFrameCallback((_) => photo.dispose());
  }

  /// Save was pressed on the sentence screen: let the reader check the picked lines and
  /// the page number, save them, and tell the Swing UI how it went.
  Future<void> _savePassage(int request, String isbn13) async {
    final review = _page;
    final lines = review?.lines;
    if (review == null || lines == null || review.picked.isEmpty) {
      final String message;
      if (review == null) {
        // No camera, or no permission to use it: there was nothing to photograph.
        message = '페이지 사진이 없어요. 카메라 권한을 확인해 주세요.';
      } else if (lines == null && !review.unreadable) {
        message = '아직 글자를 읽는 중이에요.';
      } else {
        message = '저장할 줄을 먼저 눌러 고르세요.';
      }
      _showMessage(message);
      _reply(request, PassageOutcome.nothingPicked);
      return;
    }
    final picked = review.picked.toList()..sort();
    final saved = await showPassageDialog(
      context,
      text: joinLines(picked.map((line) => lines[line].text)),
      page: guessPageNumber(lines),
      save: (text, page) => _saveThroughPage(request, isbn13, text, page),
    );
    if (mounted) _reply(request, saved ? PassageOutcome.saved : PassageOutcome.cancelled);
  }

  /// Has the page save the passage and waits for its word on how that went. The page does
  /// it because the page is signed in: the server tells whose passage it is by the session
  /// cookie, which only requests from the web view carry.
  Future<bool> _saveThroughPage(int request, String isbn13, String text, int? page) async {
    final answer = Completer<bool>();
    _saving[request]?.complete(false);
    _saving[request] = answer;
    final passage = jsonEncode({'page': page, 'text': text});
    try {
      await _web.runJavaScript('window.hostSavePassage($request, ${jsonEncode(isbn13)}, $passage)');
      return await answer.future.timeout(const Duration(seconds: 20));
    } on Exception {
      // The page is gone, or the server never answered it.
      return false;
    } finally {
      if (_saving[request] == answer) _saving.remove(request);
    }
  }

  void _reply(int request, PassageOutcome outcome) {
    _web.runJavaScript('window.hostReply && window.hostReply($request, ${outcome.index})').catchError((Object _) {});
  }

  /// "Retake" under the page photo. The Swing UI treats Back on a captured page as
  /// "another photo" and asks for the live preview again.
  void _retakePage() {
    _web.runJavaScript('window.hostBack && window.hostBack()').catchError((Object _) {});
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

  /// Whether the address is part of signing in at Kakao (kauth.kakao.com, accounts.kakao.com).
  static bool _isKakaoSignIn(Uri url) =>
      url.scheme == 'https' && (url.host == 'kakao.com' || url.host.endsWith('.kakao.com'));

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
      ..showSnackBar(SnackBar(
        content: const Text('한 번 더 누르면 앱을 종료합니다.'),
        duration: const Duration(seconds: 3),
        // The one place to switch servers while the current one still answers: between the
        // laptop's address at home and its tunnel address elsewhere.
        action: SnackBarAction(label: '서버 변경', onPressed: _changeServer),
      ));
  }

  void _showMessage(String text) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(text)));
  }

  void _reload() {
    setState(() => _loadError = null);
    _load();
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
    final review = _page;
    final pagePhoto = review?.photo;
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
              if (review != null && pagePhoto != null && place != null && _loadError == null)
                Positioned.fromRect(
                  rect: place.rect!,
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(place.radius),
                    child: PagePicker(
                      photo: pagePhoto,
                      lines: review.lines,
                      picked: review.picked,
                      unreadable: review.unreadable,
                      onChanged: (picked) => setState(() => review.picked = picked),
                      onRetake: _retakePage,
                    ),
                  ),
                )
              else if (showPreview)
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

/// A page photo under review on the sentence screen.
class _PageReview {
  /// The photo, upright; null until it has been taken and decoded.
  ui.Image? photo;

  /// The lines of text found on it; null while it is being read.
  List<PageLine>? lines;

  /// Which of [lines] the reader has picked to save.
  Set<int> picked = {};

  /// Taking the photo or reading it failed.
  bool unreadable = false;
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
