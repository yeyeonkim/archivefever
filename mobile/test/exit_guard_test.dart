import 'package:archive_fever/exit_guard.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  late DateTime clock;
  late ExitGuard guard;

  setUp(() {
    clock = DateTime(2026, 11, 23, 16);
    guard = ExitGuard(window: const Duration(seconds: 2), now: () => clock);
  });

  test('a single Back press never closes the app', () {
    expect(guard.shouldExit(), isFalse);
  });

  test('a second press soon after the first closes it', () {
    guard.shouldExit();
    clock = clock.add(const Duration(milliseconds: 800));

    expect(guard.shouldExit(), isTrue);
  });

  test('a second press that comes too late only starts over', () {
    guard.shouldExit();
    clock = clock.add(const Duration(seconds: 3));

    expect(guard.shouldExit(), isFalse);
    clock = clock.add(const Duration(seconds: 1));
    expect(guard.shouldExit(), isTrue);
  });

  test('after closing, the next press starts over', () {
    guard.shouldExit();
    expect(guard.shouldExit(), isTrue);

    expect(guard.shouldExit(), isFalse);
  });
}
