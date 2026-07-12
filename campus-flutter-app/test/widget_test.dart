import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:campus_flutter_app/information_api.dart';
import 'package:campus_flutter_app/main.dart';

// ImportResult/ImportTaskItem re-exported via information_api.dart

void main() {
  testWidgets('renders login and information home flow', (tester) async {
    final api = _FakeCampusApi();
    await tester.pumpWidget(CampusMindApp(api: api));

    expect(find.text('登录'), findsOneWidget);

    await tester.tap(find.text('登录'));
    await tester.pumpAndSettle();

    expect(find.text('今日信息集中站'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.text('创新创业竞赛报名开放'),
      220,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('创新创业竞赛报名开放'), findsOneWidget);

    await tester.tap(find.text('创新创业竞赛报名开放'));
    await tester.pumpAndSettle();

    expect(find.text('信息详情'), findsOneWidget);
    expect(find.textContaining('报名时间'), findsOneWidget);
    expect(api.statusUpdates, contains('READ'));
    expect(find.text('标记已读'), findsNothing);

    await tester.tap(find.text('收藏'));
    await tester.pumpAndSettle();
    expect(find.text('取消收藏'), findsOneWidget);
  });
}

class _FakeCampusApi implements CampusApi {
  final statusUpdates = <String>[];
  final _item = InformationItem(
    id: 1,
    title: '创新创业竞赛报名开放',
    sourceName: '软件学院创新创业通知公告',
    preview: '竞赛报名正文，包含报名时间、截止时间和所需材料。',
    originalUrl: 'https://ss.xju.edu.cn/info/1.htm',
    readStatus: 'NEW',
    itemStatus: 'ACTIVE',
    fetchedAt: DateTime(2026, 7, 9, 10),
  );

  @override
  Future<LoginSession> login(String username, String password) async {
    return LoginSession(
      accessToken: 'test-token',
      tokenType: 'Bearer',
      expiresAt: DateTime(2026, 7, 9, 12),
      user: const CampusUser(id: 1, username: 'admin', role: 'ADMIN'),
    );
  }

  @override
  Future<List<InformationItem>> fetchInformationFeed(
      LoginSession? session) async {
    return [_item];
  }

  @override
  Future<InformationItem> fetchInformationDetail(
      int id, LoginSession? session) async {
    return _item.copyWith(
      preview: '竞赛报名正文，包含报名时间、截止时间、持续时间、所需材料和报名网址。',
    );
  }

  @override
  Future<InformationItem> updateReadStatus(
    int id,
    String readStatus,
    LoginSession session,
  ) async {
    statusUpdates.add(readStatus);
    return _item.copyWith(readStatus: readStatus);
  }

  @override
  Future<ImportResult> importText(String text, LoginSession session) async {
    return const ImportResult(taskId: 1, status: 'SUCCESS', message: 'ok');
  }

  @override
  Future<ImportResult> importImage(
      String base64, String? name, LoginSession session) async {
    return const ImportResult(taskId: 2, status: 'PENDING', message: 'ok');
  }

  @override
  Future<ImportResult> importFile(
      List<int> bytes, String fileName, LoginSession session) async {
    return const ImportResult(taskId: 3, status: 'SUCCESS', message: 'ok');
  }

  @override
  Future<ImportResult> importRainJson(
      String dataType, String rawJson, LoginSession session) async {
    return const ImportResult(taskId: 4, status: 'SUCCESS', message: 'ok');
  }

  @override
  Future<ImportResult> importRainCookie(
      String cookie, List<String> scopes, LoginSession session) async {
    return const ImportResult(taskId: 5, status: 'PENDING', message: 'ok');
  }

  @override
  Future<List<ImportTaskItem>> fetchImportTasks(LoginSession session) async {
    return const [];
  }

  @override
  Future<AiChatResult> aiChat(String sessionId, String message, LoginSession session) async {
    return const AiChatResult(sessionId: 's1', answer: 'ok');
  }

  @override
  Future<SearchResult> search(String query, LoginSession session) async {
    return const SearchResult(items: [], total: 0);
  }

  @override
  Future<UserProfile> fetchMe(LoginSession session) async {
    return const UserProfile(id: 1, username: 'admin');
  }
}
