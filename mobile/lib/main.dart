import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'screens/server_setup_screen.dart';
import 'screens/shell_screen.dart';
import 'settings.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // The Swing UI is a fixed portrait design.
  await SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
  runApp(ArchiveFeverApp(settings: await Settings.load()));
}

class ArchiveFeverApp extends StatelessWidget {
  const ArchiveFeverApp({super.key, required this.settings});

  final Settings settings;

  @override
  Widget build(BuildContext context) {
    final server = settings.server;
    return MaterialApp(
      title: 'Archive Fever',
      theme: ThemeData(colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF6B3A2E))),
      // Once an address has worked it is reused; the web view offers to change it on failure.
      home: server == null
          ? ServerSetupScreen(settings: settings)
          : ShellScreen(server: server, settings: settings),
    );
  }
}
