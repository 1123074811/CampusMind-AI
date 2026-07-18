import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:campus_flutter_app/detail_page.dart';
import 'package:campus_flutter_app/discover_page.dart';
import 'package:campus_flutter_app/home_page.dart';
import 'package:campus_flutter_app/assistant_page.dart';
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

  test('shows importance only for time-sensitive event types', () {
    final now = DateTime(2026, 7, 15, 12);
    InformationItem event(String type, String deadline) => InformationItem(
          id: 1,
          title: '测试事件',
          sourceName: '教务处',
          preview: '',
          originalUrl: 'https://example.edu/1',
          readStatus: 'NEW',
          itemStatus: 'ACTIVE',
          fetchedAt: now,
          eventType: type,
          aiCard: {'registrationDeadline': deadline},
        );

    expect(event('NOTICE', '2026-07-16 12:00').importanceLevelAt(now), isNull);
    expect(event('COMPETITION', '2026年7月17日 12:00').importanceLevelAt(now),
        'urgent');
    expect(event('EXAM', '2026-07-20 12:00').importanceLevelAt(now), 'high');
    expect(
        event('ACTIVITY', '2026-07-10 12:00').importanceLevelAt(now), isNull);
  });

  testWidgets('home exposes all and subscribed-only feed modes',
      (tester) async {
    var selectedMode = 'ALL';
    await tester.pumpWidget(MaterialApp(
      home: PrototypeHomePage(
        items: const [],
        onOpenDetail: (_) {},
        onOpenImport: () {},
        userName: 'tester',
        api: _FakeCampusApi(_item()),
        session: _session(),
        total: 0,
        feedMode: selectedMode,
        onFeedModeChanged: (mode) => selectedMode = mode,
      ),
    ));

    await tester.tap(find.text('仅订阅'));
    await tester.pump();

    expect(selectedMode, 'SUBSCRIBED_ONLY');
  });

  testWidgets('assistant keeps chat messages while switching tabs',
      (tester) async {
    var index = 1;
    late StateSetter updateTabs;
    await tester.pumpWidget(MaterialApp(
      home: StatefulBuilder(builder: (context, setState) {
        updateTabs = setState;
        return Scaffold(
          body: Stack(
            fit: StackFit.expand,
            children: [
              Offstage(
                offstage: index != 1,
                child: PrototypeAssistantPage(
                    api: _FakeCampusApi(_item()), session: _session()),
              ),
              if (index != 1) const Text('其他页面'),
            ],
          ),
        );
      }),
    ));

    await tester.enterText(find.byType(TextField), '记住我喜欢讲座');
    await tester.tap(find.byIcon(Icons.send));
    await tester.pumpAndSettle();
    expect(find.text('记住我喜欢讲座'), findsOneWidget);

    updateTabs(() => index = 0);
    await tester.pump();
    updateTabs(() => index = 1);
    await tester.pump();

    expect(find.text('记住我喜欢讲座'), findsOneWidget);
    expect(find.textContaining('已记录'), findsOneWidget);
  });

  testWidgets('detail page separates a persisted AI summary from the body',
      (tester) async {
    final item = _item(aiStatus: 'SUCCESS', aiSummary: '模型生成的三点摘要。');
    await tester.pumpWidget(_detailApp(item));
    await tester.pumpAndSettle();

    expect(find.text('AI 智能摘要'), findsOneWidget);
    expect(find.text('模型生成的三点摘要。'), findsOneWidget);
    await tester.drag(find.byType(ListView).first, const Offset(0, -600));
    await tester.pumpAndSettle();
    expect(find.textContaining('这里是完整原文正文。'), findsOneWidget);
    await tester.drag(find.byType(ListView).first, const Offset(0, -600));
    await tester.pumpAndSettle();
    expect(find.text('查看原文'), findsOneWidget);
    expect(find.text('发布时间：2026-07-09 09:00'), findsOneWidget);
    expect(find.text('内容哈希：aaaaaaaa…aaaaaaaa'), findsOneWidget);
    expect(find.text('紧急'), findsNothing);
  });

  testWidgets(
      'detail page does not show an AI label when summary is unavailable',
      (tester) async {
    final item = _item(aiStatus: 'FAILED', aiSummary: '不应展示的旧摘要');
    await tester.pumpWidget(_detailApp(item));
    await tester.pumpAndSettle();

    expect(find.text('AI 智能摘要'), findsNothing);
    expect(find.text('不应展示的旧摘要'), findsNothing);
    await tester.drag(find.byType(ListView).first, const Offset(0, -600));
    await tester.pumpAndSettle();
    expect(find.textContaining('这里是完整原文正文。'), findsOneWidget);
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
      aiCard: const {
        'requiredActions': ['完成报名']
      },
    );
    final api = _FakeCampusApi(item);
    await tester.pumpWidget(_detailApp(item, api));
    await tester.pumpAndSettle();

    expect(find.text('建议行动'), findsOneWidget);
    await tester.tap(find.text('加入'));
    await tester.pumpAndSettle();

    expect(api.confirmedAction, '完成报名');
    expect(find.text('已加入'), findsOneWidget);
    expect(find.text('加入'), findsNothing);

    await tester.pumpWidget(const SizedBox());
    await tester.pumpWidget(_detailApp(item, api));
    await tester.pumpAndSettle();

    expect(find.text('已加入'), findsOneWidget);
    expect(find.text('加入'), findsNothing);
  });

  testWidgets('discover page reports action failures without demo content',
      (tester) async {
    final api = _FailingActionsApi(_item());
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: PrototypeDiscoverPage(api: api, session: _session()),
      ),
    ));
    await tester.pumpAndSettle();

    expect(find.textContaining('待办加载失败'), findsOneWidget);
    expect(find.text('选课系统维护通知'), findsNothing);
  });

  testWidgets('todo cards can be cancelled, completed and opened',
      (tester) async {
    tester.view.physicalSize = const Size(800, 1200);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final api = _ActionsApi(_item());
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: PrototypeDiscoverPage(api: api, session: _session()),
      ),
    ));
    await tester.pumpAndSettle();

    expect(find.text('待完成 2'), findsOneWidget);
    expect(find.text('已完成 1'), findsOneWidget);
    await tester.ensureVisible(find.text('取消待办').first);
    await tester.tap(find.text('取消待办').first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('确认取消'));
    await tester.pumpAndSettle();
    expect(api.cancelledId, 1);

    await tester.ensureVisible(find.text('完成'));
    await tester.tap(find.text('完成'));
    await tester.pumpAndSettle();
    expect(api.completedId, 2);
    await tester.tap(find.text('已完成 2'));
    await tester.pumpAndSettle();
    expect(find.text('已完成事项'), findsOneWidget);

    await tester.tap(find.text('已完成事项'));
    await tester.pumpAndSettle();
    expect(find.text('测试通知'), findsOneWidget);
  });
}

LoginSession _session() => LoginSession(
      accessToken: 'test-token',
      tokenType: 'Bearer',
      expiresAt: DateTime(2026, 7, 10),
      user: const CampusUser(id: 1, username: 'tester', role: 'STUDENT'),
    );

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
      publishTime: DateTime(2026, 7, 9, 9),
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
        session: _session(),
        onItemChanged: (_) {},
      ),
    );

class _FakeCampusApi extends CampusApi {
  _FakeCampusApi(this.item);
  final InformationItem item;
  String? confirmedAction;

  @override
  Future<InformationItem> fetchInformationDetail(
          int id, LoginSession? session) async =>
      item;

  @override
  Future<InformationItem> updateReadStatus(
          int id, String readStatus, LoginSession session) async =>
      item;

  @override
  Future<void> confirmAction(
      int itemId, String title, LoginSession session) async {
    confirmedAction = title;
  }

  @override
  Future<List<ActionItem>> fetchActions(LoginSession session) async =>
      confirmedAction == null
          ? const []
          : [
              ActionItem(
                id: 1,
                informationItemId: item.id,
                title: confirmedAction!,
                status: 'CONFIRMED',
              )
            ];

  @override
  Future<LoginSession> login(String username, String password) =>
      throw UnimplementedError();
  @override
  Future<List<InformationItem>> fetchInformationFeed(LoginSession? session) =>
      throw UnimplementedError();
  @override
  Future<ImportResult> importText(String text, LoginSession session) =>
      throw UnimplementedError();
  @override
  Future<ImportResult> importImage(
          String base64, String? name, LoginSession session) =>
      throw UnimplementedError();
  @override
  Future<ImportResult> importFile(
          List<int> bytes, String fileName, LoginSession session) =>
      throw UnimplementedError();
  @override
  Future<ImportResult> importRainJson(
          String dataType, String rawJson, LoginSession session) =>
      throw UnimplementedError();
  @override
  Future<ImportResult> importRainCookie(
          String cookie, List<String> scopes, LoginSession session) =>
      throw UnimplementedError();
  @override
  Future<List<ImportTaskItem>> fetchImportTasks(LoginSession session) =>
      throw UnimplementedError();
  @override
  Future<void> confirmImportPreview({
    required int taskId,
    required String title,
    String time = '',
    String location = '',
    String summary = '',
    List<String> tags = const [],
    String? eventType,
    required LoginSession session,
  }) =>
      throw UnimplementedError();
  @override
  Future<List<ImportedEventItem>> fetchRainEvents(LoginSession session) =>
      throw UnimplementedError();
  @override
  Future<AiChatResult> aiChat(
          String sessionId, String message, LoginSession session) async =>
      const AiChatResult(sessionId: 'memory-session', answer: '已记录你的偏好。');
  @override
  Future<SearchResult> search(String query, LoginSession session) =>
      throw UnimplementedError();
  @override
  Future<UserProfile> fetchMe(LoginSession session) =>
      throw UnimplementedError();
  @override
  Future<UserStats> fetchStats(LoginSession session) =>
      throw UnimplementedError();
  @override
  Future<List<InformationItem>> fetchFavorites(LoginSession session) =>
      throw UnimplementedError();
  @override
  Future<List<InformationItem>> fetchReadHistory(LoginSession session) =>
      throw UnimplementedError();
  @override
  Future<List<SubscriptionItem>> fetchSubscriptions(LoginSession session) =>
      throw UnimplementedError();
  @override
  Future<SubscriptionItem> updateSubscription(
          int sourceId, bool enabled, LoginSession session) =>
      throw UnimplementedError();
}

class _FailingActionsApi extends _FakeCampusApi {
  _FailingActionsApi(super.item);

  @override
  Future<List<ActionItem>> fetchActions(LoginSession session) =>
      Future.error(Exception('network unavailable'));
}

class _ActionsApi extends _FakeCampusApi {
  _ActionsApi(super.item);
  int? completedId;
  int? cancelledId;

  @override
  Future<List<ActionItem>> fetchActions(LoginSession session) async => [
        _action(1, '取消事项'),
        _action(2, '完成事项'),
        _action(3, '已完成事项', status: 'COMPLETED'),
      ];

  @override
  Future<void> completeAction(int actionId, LoginSession session) async {
    completedId = actionId;
  }

  @override
  Future<void> cancelAction(int actionId, LoginSession session) async {
    cancelledId = actionId;
  }

  static ActionItem _action(int id, String title,
          {String status = 'CONFIRMED'}) =>
      ActionItem(
        id: id,
        informationItemId: 1,
        title: title,
        status: status,
        sourceTitle: '测试通知',
      );
}
