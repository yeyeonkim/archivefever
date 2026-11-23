import 'package:shared_preferences/shared_preferences.dart';

import 'server.dart';

/// Remembers the scan a photo is being taken for.
///
/// Android may stop the app while the camera is open. The id has to survive that so the
/// recovered photo can still be uploaded to the right scan.
abstract class PendingScanStore {
  Future<String?> read();
  Future<void> save(String scanId);
  Future<void> clear();
}

/// Values kept between launches.
class Settings implements PendingScanStore {
  Settings(this._preferences);

  static const _serverKey = 'server';
  static const _pendingScanKey = 'pendingScan';

  final SharedPreferences _preferences;

  static Future<Settings> load() async => Settings(await SharedPreferences.getInstance());

  Server? get server {
    final saved = _preferences.getString(_serverKey);
    return saved == null ? null : Server.parse(saved);
  }

  Future<void> saveServer(Server server) => _preferences.setString(_serverKey, server.toString());

  @override
  Future<String?> read() async => _preferences.getString(_pendingScanKey);

  @override
  Future<void> save(String scanId) => _preferences.setString(_pendingScanKey, scanId);

  @override
  Future<void> clear() => _preferences.remove(_pendingScanKey);
}
