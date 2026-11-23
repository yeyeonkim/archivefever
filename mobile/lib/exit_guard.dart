/// Decides whether a Back press should close the app.
///
/// Leaving the app throws away the running Swing UI, which takes a long time to start
/// again, so one press must never do it: only the second of two quick presses does.
class ExitGuard {
  ExitGuard({this.window = const Duration(seconds: 2), DateTime Function()? now})
      : _now = now ?? DateTime.now;

  /// How soon the second press has to follow the first.
  final Duration window;

  final DateTime Function() _now;
  DateTime? _armedAt;

  /// Call on each Back press. True means: close the app now.
  bool shouldExit() {
    final now = _now();
    final armedAt = _armedAt;
    if (armedAt != null && now.difference(armedAt) <= window) {
      _armedAt = null;
      return true;
    }
    _armedAt = now;
    return false;
  }
}
