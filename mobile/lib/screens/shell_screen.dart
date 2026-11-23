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
/// take photos with the camera and open outside links.
class ShellScreen extends StatefulWidget {
  const ShellScreen({super.key, required this.server, required this.settings});

  final Server server;
  final Settings settings;

  @override
  State<ShellScreen> createState() => _ShellScreenState();
}

class _ShellScreenState extends State<ShellScreen> {
  static const Color _backdrop = Color(0xFF2B211A);

  late final ScanService _scans = ScanService(server: widget.server, pending: widget.settings);
  final ExitGuard _exitGuard = ExitGuard();
  late final WebViewController _web;
  bool _uploading = false;
  String? _loadError;

  @override
  void initState() {
    super.initState();
    _web = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(_backdrop)
      ..addJavaScriptChannel('Bridge', onMessageReceived: (message) => _handle(message.message))
      ..setNavigationDelegate(NavigationDelegate(
        onNavigationRequest: (request) {
          final target = Uri.tryParse(request.url);
          if (target != null && widget.server.owns(target)) return NavigationDecision.navigate;
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

  /// A photo may have been taken just before Android stopped the app for the camera.
  Future<void> _recover() async {
    final outcome = await _scans.recoverLostPhoto();
    if (mounted && outcome == ScanOutcome.failed) _showMessage('사진을 올리지 못했어요.');
  }

  void _handle(String raw) {
    switch (BridgeMessage.parse(raw)) {
      case ScanRequest(:final scanId):
        _scan(scanId);
      case OpenUrlRequest(:final url):
        _open(url);
      case PageReady():
      case null:
        break;
    }
  }

  Future<void> _scan(String scanId) async {
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
