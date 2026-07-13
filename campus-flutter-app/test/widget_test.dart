import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:campus_flutter_app/detail_page.dart';
import 'package:campus_flutter_app/information_api.dart';

void main() {
  test('keeps the persisted AI summary returned by the backend', () {
    final item = InformationItem.fromJson({
      'id': 1,
      'title': '创新创业竞赛报名开放',
      'sourceName': '软件学院',
      'fetchedAt': '2026-07-09T10:00:00',
      'aiStatus': 'SUCCESS',
      'aiSummary': '模型生成的竞赛报名摘要。',
      'originalUrl': 'https://example.edu/notice/1',
      'contentHash': List.filled(64, 'a').join(),
    });

    expect(item.aiSummary, '模型生成的竞赛报名摘要。');
    expect(item.hasValidAiSummary, isTrue);
    expect(item.safeOriginalUri?.host, 'example.edu');
    expect(item.contentHash.length, 64);
  });

  test('does not label fallback content as an AI summary', () {
    final item = InformationItem.fromJson({
      'id': 2,
      'title': '普通通知',
      'sourceName': '教务处',
      'fetchedAt': '2026-07-09T10:00:00',
      'aiStatus': 'FAILED',
      'aiSummary': '这是旧的摘要内容。',
      'originalUrl': 'javascript:alert(1)',
    });

    expect(item.hasValidAiSummary, isFalse);
    expect(item.safeOriginalUri, isNull);
  });

  testWidgets('detail page separates a persisted AI summary from the body',
      (tester) async {
    final item = _item(aiStatus: 'SUCCESS', aiSummary: '模型生成的三点摘要。');
    await tester.pumpWidget(_detailApp(item));
    await tester.pumpAndSettle();

    expect(find.text('AI 智能摘要'), findsOneWidget);
    expect(find.text('模型生成的三点摘要。'), findsOneWidget);
    expect(find.text('这里是完整原文正文。'), findsOneWidget);
    expect(find.text('查看原文'), findsOneWidget);
  });

  testWidgets('detail page does not show an AI label when summary is unavailable',
      (tester) async {
    final item = _item(aiStatus: 'FAILED', aiSummary: '不应展示的旧摘要');
    await tester.pumpWidget(_detailApp(item));
    await tester.pumpAndSettle();

    expect(find.text('AI 智能摘要'), findsNothing);
    expect(find.text('不应展示的旧摘要'), findsNothing);
    expect(find.text('这里是完整原文正文。'), findsOneWidget);
  });

  testWidgets('detail page hides an unsafe original URL', (tester) async {
    final item = _item(originalUrl: 'javascript:alert(1)');
    await tester.pumpWidget(_detailApp(item));
    await tester.pumpAndSettle();

    expect(find.text('查看原文'), findsNothing);
    expect(find.text('来源与校验信息'), findsOneWidget);
  });

  testWidgets('user explicitly confirms a reviewed AI action', (tester) async {
    final item = _item(
      aiStatus: 'SUCCESS',
      aiSummary: '报名摘要',
      aiCard: const {'requiredActions': ['完成报名']},
    );
    final api = _FakeCampusApi(item);
    await tester.pumpWidget(_detailApp(item, api));
    await tester.pumpAndSettle();

    expect(find.text('建议行动'), findsOneWidget);
    await tester.tap(find.text('确认加入'));
    await tester.pumpAndSettle();

    expect(api.confirmedAction, '完成报名');
    expect(find.byIcon(Icons.check_circle), findsOneWidget);
  });
}

InformationItem _item({
  String aiStatus = 'PENDING',
  String aiSummary = '',
  String originalUrl = 'https://example.edu/notices/1',
  Map<String, Object?> aiCard = const {},
}) =>
    InformationItem(
      id: 1,
      title: '测试通知',
      sourceName: '软件学院',
      preview: '正文预览',
      detailContent: '这里是完整原文正文。',
      originalUrl: originalUrl,
      sourceUrl: 'https://example.edu',
      contentHash: List.filled(64, 'a').join(),
      readStatus: 'READ',
      itemStatus: 'ACTIVE',
      fetchedAt: DateTime(2026, 7, 9, 10),
      aiStatus: aiStatus,
      eventType: 'NOTICE',
      aiSummary: aiSummary,
      aiCard: aiCard,
    );

Widget _detailApp(InformationItem item, [CampusApi? api]) => MaterialApp(
      home: PrototypeDetailPage(
        item: item,
        api: api ?? _FakeCampusApi(item),
        session: LoginSession(
          accessToken: 'test-token',
          tokenType: 'Bearer',
          expiresAt: DateTime(2026, 7, 10),
          user: const CampusUser(id: 1, username: 'tester', role: 'STUDENT'),
        ),
        onItemChanged: (_) {},
      ),
    );

class _FakeCampusApi implements CampusApi {
  _FakeCampusApi(this.item);
  final InformationItem item;
  String? confirmedAction;

  @override
  Future<InformationItem> fetchInformationDetail(int id, LoginSession? session) async => item;

  @override
  Future<InformationItem> updateReadStatus(int id, String readStatus, LoginSession session) async => item;

  @override
  Future<void> confirmAction(int itemId, String title, LoginSession session) async {
    confirmedAction = title;
  }

  @override
  Future<LoginSession> login(String username, String password) => throw UnimplementedError();
  @override
  Future<List<InformationItem>> fetchInformationFeed(LoginSession? session) => throw UnimplementedError();
  @override
  Future<ImportResult> importText(String text, LoginSession session) => throw UnimplementedError();
  @override
  Future<ImportResult> importImage(String base64, String? name, LoginSession session) => throw UnimplementedError();
  @override
  Future<ImportResult> importFile(List<int> bytes, String fileName, LoginSession session) => throw UnimplementedError();
  @override
  Future<ImportResult> importRainJson(String dataType, String rawJson, LoginSession session) => throw UnimplementedError();
  @override
  Future<ImportResult> importRainCookie(String cookie, List<String> scopes, LoginSession session) => throw UnimplementedError();
  @override
  Future<List<ImportTaskItem>> fetchImportTasks(LoginSession session) => throw UnimplementedError();
  @override
  Future<List<ImportedEventItem>> fetchRainEvents(LoginSession session) => throw UnimplementedError();
  @override
  Future<AiChatResult> aiChat(String sessionId, String message, LoginSession session) => throw UnimplementedError();
  @override
  Future<SearchResult> search(String query, LoginSession session) => throw UnimplementedError();
  @override
  Future<UserProfile> fetchMe(LoginSession session) => throw UnimplementedError();
  @override
  Future<UserStats> fetchStats(LoginSession session) => throw UnimplementedError();
  @override
  Future<List<InformationItem>> fetchFavorites(LoginSession session) => throw UnimplementedError();
  @override
  Future<List<InformationItem>> fetchReadHistory(LoginSession session) => throw UnimplementedError();
  @override
  Future<List<SubscriptionItem>> fetchSubscriptions(LoginSession session) => throw UnimplementedError();
  @override
  Future<SubscriptionItem> updateSubscription(int sourceId, bool enabled, LoginSession session) => throw UnimplementedError();
}
