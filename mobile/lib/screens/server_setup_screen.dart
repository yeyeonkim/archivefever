import 'dart:async';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import '../server.dart';
import '../settings.dart';
import 'shell_screen.dart';

/// Asks for the laptop's address and checks that the Archive Fever server answers there.
class ServerSetupScreen extends StatefulWidget {
  const ServerSetupScreen({super.key, required this.settings, this.client});

  final Settings settings;
  final http.Client? client;

  @override
  State<ServerSetupScreen> createState() => _ServerSetupScreenState();
}

class _ServerSetupScreenState extends State<ServerSetupScreen> {
  late final TextEditingController _address =
      TextEditingController(text: widget.settings.server?.toString() ?? '');
  bool _checking = false;
  String? _error;

  @override
  void dispose() {
    _address.dispose();
    super.dispose();
  }

  Future<void> _connect() async {
    final server = Server.parse(_address.text);
    if (server == null) {
      setState(() => _error = '주소를 확인해 주세요. 예: 192.168.0.10:8080');
      return;
    }
    setState(() {
      _checking = true;
      _error = null;
    });
    final reachable = await _answers(server);
    if (!mounted) return;
    if (!reachable) {
      setState(() {
        _checking = false;
        _error = '$server 에서 서버가 응답하지 않아요.\n노트북과 같은 Wi-Fi인지, 서버가 켜져 있는지 확인해 주세요.';
      });
      return;
    }
    await widget.settings.saveServer(server);
    if (!mounted) return;
    Navigator.of(context).pushReplacement(MaterialPageRoute<void>(
      builder: (_) => ShellScreen(server: server, settings: widget.settings),
    ));
  }

  Future<bool> _answers(Server server) async {
    final client = widget.client ?? http.Client();
    try {
      final response =
          await client.get(server.resolve('/api/health')).timeout(const Duration(seconds: 5));
      return response.statusCode == 200 && response.body.contains('"ok"');
    } on Exception {
      return false;
    } finally {
      if (widget.client == null) client.close();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Archive Fever', textAlign: TextAlign.center, style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: 32),
              TextField(
                controller: _address,
                enabled: !_checking,
                keyboardType: TextInputType.url,
                autocorrect: false,
                decoration: InputDecoration(
                  labelText: '서버 주소',
                  hintText: '192.168.0.10:8080',
                  errorText: _error,
                  errorMaxLines: 4,
                  border: const OutlineInputBorder(),
                ),
                onSubmitted: (_) => _connect(),
              ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: _checking ? null : _connect,
                child: Text(_checking ? '확인 중…' : '접속'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
