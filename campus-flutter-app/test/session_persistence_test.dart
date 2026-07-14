import 'package:flutter_test/flutter_test.dart';
import 'package:campus_flutter_app/information_api.dart';

void main() {
  test('LoginSession round-trips through JSON for persistence', () {
    final session = LoginSession(
      accessToken: 'access-1',
      tokenType: 'Bearer',
      expiresAt: DateTime.utc(2026, 7, 14, 12),
      refreshToken: 'refresh-1',
      refreshExpiresAt: DateTime.utc(2026, 8, 14, 12),
      user: const CampusUser(id: 7, username: 'alice', role: 'STUDENT'),
    );

    final restored = LoginSession.fromJson(session.toJson());
    expect(restored.accessToken, 'access-1');
    expect(restored.refreshToken, 'refresh-1');
    expect(restored.user.id, 7);
    expect(restored.user.username, 'alice');
    expect(restored.expiresAt.toUtc(), DateTime.utc(2026, 7, 14, 12));
    expect(restored.refreshExpiresAt?.toUtc(), DateTime.utc(2026, 8, 14, 12));
  });
}
