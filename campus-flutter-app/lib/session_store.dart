import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import 'information_api.dart';

/// 本地会话持久化。仅保存令牌与用户标识，不落库明文密码。
class SessionStore {
  static const _key = 'campusmind.login_session.v1';

  Future<void> save(LoginSession session) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, jsonEncode(session.toJson()));
  }

  Future<LoginSession?> load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_key);
    if (raw == null || raw.isEmpty) return null;
    try {
      final map = jsonDecode(raw) as Map<String, Object?>;
      final session = LoginSession.fromJson(map);
      if (session.accessToken.isEmpty || session.refreshToken.isEmpty) {
        await clear();
        return null;
      }
      if (session.refreshExpiresAt != null &&
          session.refreshExpiresAt!.isBefore(DateTime.now())) {
        await clear();
        return null;
      }
      return session;
    } catch (_) {
      await clear();
      return null;
    }
  }

  Future<void> clear() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_key);
  }
}
