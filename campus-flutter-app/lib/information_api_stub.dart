/// 会话过期异常，API 层在收到 401 时抛出。
class SessionExpiredException implements Exception {
  const SessionExpiredException([this.message = '登录已过期，请重新登录']);
  final String message;
  @override
  String toString() => message;
}

class CampusUser {
  const CampusUser({
    required this.id,
    required this.username,
    required this.role,
  });

  final int id;
  final String username;
  final String role;

  factory CampusUser.fromJson(Map<String, Object?> json) {
    return CampusUser(
      id: (json['id'] as num?)?.toInt() ?? 0,
      username: json['username'] as String? ?? 'unknown',
      role: json['role'] as String? ?? 'STUDENT',
    );
  }
}

class LoginSession {
  const LoginSession({
    required this.accessToken,
    required this.tokenType,
    required this.expiresAt,
    required this.user,
    this.refreshToken = '',
    this.refreshExpiresAt,
  });

  final String accessToken;
  final String tokenType;
  final DateTime expiresAt;
  final CampusUser user;
  final String refreshToken;
  final DateTime? refreshExpiresAt;

  factory LoginSession.fromJson(Map<String, Object?> json) {
    return LoginSession(
      accessToken: json['accessToken'] as String? ?? '',
      tokenType: json['tokenType'] as String? ?? 'Bearer',
      expiresAt: DateTime.tryParse(json['expiresAt'] as String? ?? '') ??
          DateTime.now().add(const Duration(hours: 2)),
      refreshToken: json['refreshToken'] as String? ?? '',
      refreshExpiresAt:
          DateTime.tryParse(json['refreshExpiresAt'] as String? ?? ''),
      user: CampusUser.fromJson(
          json['user'] as Map<String, Object?>? ?? const {}),
    );
  }

  Map<String, Object?> toJson() => {
        'accessToken': accessToken,
        'tokenType': tokenType,
        'expiresAt': expiresAt.toIso8601String(),
        'refreshToken': refreshToken,
        if (refreshExpiresAt != null)
          'refreshExpiresAt': refreshExpiresAt!.toIso8601String(),
        'user': {
          'id': user.id,
          'username': user.username,
          'role': user.role,
        },
      };
}

class InformationItem {
  static const _priorityEventTypes = {
    'EXAM',
    'HOMEWORK',
    'COMPETITION',
    'ACTIVITY',
  };

  const InformationItem({
    required this.id,
    required this.title,
    required this.sourceName,
    required this.preview,
    this.detailContent = '',
    required this.originalUrl,
    this.sourceUrl = '',
    this.contentHash = '',
    required this.readStatus,
    required this.itemStatus,
    required this.fetchedAt,
    this.aiStatus = 'PENDING',
    this.eventType = 'OTHER',
    this.aiSummary = '',
    this.aiCard = const {},
    this.aiNeedReview = false,
    this.publishTime,
    this.recommendReasons = const [],
  });

  final int id;
  final String title;
  final String sourceName;
  final String preview;
  final String detailContent;
  final String originalUrl;
  final String sourceUrl;
  final String contentHash;
  final String readStatus;
  final String itemStatus;
  final DateTime fetchedAt;
  final String aiStatus;
  final String eventType;
  final String aiSummary;
  final Map<String, Object?> aiCard;
  final bool aiNeedReview;
  final DateTime? publishTime;
  final List<String> recommendReasons;

  bool get hasValidAiSummary =>
      aiSummary.trim().isNotEmpty &&
      (aiStatus == 'SUCCESS' || aiStatus == 'REVIEW');

  String? importanceLevelAt(DateTime now) {
    if (!_priorityEventTypes.contains(eventType.toUpperCase())) return null;
    final due = _firstAiDate(aiCard, const [
      'registrationDeadline',
      'dueAt',
      'deadline',
      'endTime',
      'startTime',
    ]);
    if (due == null || due.isBefore(now)) return null;
    final hours = due.difference(now).inHours;
    if (hours <= 72) return 'urgent';
    if (hours <= 168) return 'high';
    return null;
  }

  List<String> get confirmableActions => aiStatus == 'SUCCESS' && !aiNeedReview
      ? (aiCard['requiredActions'] as List<Object?>? ?? const [])
          .whereType<String>()
          .where((value) => value.trim().isNotEmpty)
          .toList()
      : const [];

  Uri? get safeOriginalUri {
    final uri = Uri.tryParse(originalUrl.trim());
    return uri != null &&
            (uri.scheme == 'http' || uri.scheme == 'https') &&
            uri.host.isNotEmpty
        ? uri
        : null;
  }

  String get displayTime {
    final value = publishTime ?? fetchedAt;
    if (publishTime != null &&
        value.hour == 0 &&
        value.minute == 0 &&
        value.second == 0) {
      return '${value.year.toString().padLeft(4, '0')}-'
          '${value.month.toString().padLeft(2, '0')}-'
          '${value.day.toString().padLeft(2, '0')}';
    }
    return '${value.year.toString().padLeft(4, '0')}-'
        '${value.month.toString().padLeft(2, '0')}-'
        '${value.day.toString().padLeft(2, '0')} '
        '${value.hour.toString().padLeft(2, '0')}:'
        '${value.minute.toString().padLeft(2, '0')}';
  }

  String get fetchedDisplayTime =>
      '${fetchedAt.year.toString().padLeft(4, '0')}-'
      '${fetchedAt.month.toString().padLeft(2, '0')}-'
      '${fetchedAt.day.toString().padLeft(2, '0')} '
      '${fetchedAt.hour.toString().padLeft(2, '0')}:'
      '${fetchedAt.minute.toString().padLeft(2, '0')}';

  factory InformationItem.fromJson(Map<String, Object?> json) {
    return InformationItem(
      id: (json['id'] as num).toInt(),
      title: json['title'] as String? ?? '未命名信息',
      sourceName: json['sourceName'] as String? ?? '未知来源',
      preview: (json['preview'] ?? json['detailContent']) as String? ?? '',
      detailContent: json['detailContent'] as String? ?? '',
      originalUrl: (json['originalUrl'] ?? json['itemUrl']) as String? ?? '',
      sourceUrl: json['sourceUrl'] as String? ?? '',
      contentHash: json['contentHash'] as String? ?? '',
      readStatus: json['readStatus'] as String? ?? 'NEW',
      itemStatus: json['itemStatus'] as String? ?? 'ACTIVE',
      fetchedAt: DateTime.parse(json['fetchedAt'] as String),
      aiStatus: json['aiStatus'] as String? ?? 'PENDING',
      eventType: json['eventType'] as String? ?? 'OTHER',
      aiSummary: json['aiSummary'] as String? ?? '',
      aiCard: (json['aiCard'] as Map?)?.cast<String, Object?>() ?? const {},
      aiNeedReview: json['aiNeedReview'] as bool? ?? false,
      publishTime: json['publishTime'] == null
          ? null
          : DateTime.tryParse(json['publishTime'] as String),
      recommendReasons: (json['recommendReasons'] as List<Object?>? ?? const [])
          .whereType<String>()
          .toList(),
    );
  }

  InformationItem copyWith({
    String? preview,
    String? detailContent,
    String? readStatus,
    String? itemStatus,
    String? aiSummary,
  }) {
    return InformationItem(
      id: id,
      title: title,
      sourceName: sourceName,
      preview: preview ?? this.preview,
      detailContent: detailContent ?? this.detailContent,
      originalUrl: originalUrl,
      sourceUrl: sourceUrl,
      contentHash: contentHash,
      readStatus: readStatus ?? this.readStatus,
      itemStatus: itemStatus ?? this.itemStatus,
      fetchedAt: fetchedAt,
      aiStatus: aiStatus,
      eventType: eventType,
      aiSummary: aiSummary ?? this.aiSummary,
      aiCard: aiCard,
      aiNeedReview: aiNeedReview,
      publishTime: publishTime,
      recommendReasons: recommendReasons,
    );
  }
}

class InformationFeedPage {
  const InformationFeedPage({
    required this.items,
    this.nextCursor,
    this.nextCursorId,
    this.nextSubscriptionMatch,
    required this.hasMore,
    required this.total,
  });

  final List<InformationItem> items;
  final String? nextCursor;
  final int? nextCursorId;
  final int? nextSubscriptionMatch;
  final bool hasMore;
  final int total;

  factory InformationFeedPage.fromJson(Map<String, Object?> json) {
    final items = json['items'] as List<Object?>? ?? const [];
    return InformationFeedPage(
      items: items
          .cast<Map<String, Object?>>()
          .map(InformationItem.fromJson)
          .toList(),
      nextCursor: json['nextCursor'] as String?,
      nextCursorId: (json['nextCursorId'] as num?)?.toInt(),
      nextSubscriptionMatch:
          (json['nextSubscriptionMatch'] as num?)?.toInt(),
      hasMore: json['hasMore'] as bool? ?? false,
      total: (json['total'] as num?)?.toInt() ?? items.length,
    );
  }
}

DateTime? _firstAiDate(Map<String, Object?> card, List<String> keys) {
  for (final key in keys) {
    final raw = card[key];
    if (raw is! String || raw.trim().isEmpty) continue;
    final normalized = raw.trim()
        .replaceAll('年', '-')
        .replaceAll('月', '-')
        .replaceAll('日', '')
        .replaceAll('/', '-');
    final parsed = DateTime.tryParse(normalized);
    if (parsed != null) return parsed;
    final parts = RegExp(
            r'^(\d{4})-(\d{1,2})-(\d{1,2})(?:\s+(\d{1,2}):(\d{1,2}))?')
        .firstMatch(normalized);
    if (parts != null) {
      return DateTime(
        int.parse(parts[1]!),
        int.parse(parts[2]!),
        int.parse(parts[3]!),
        int.tryParse(parts[4] ?? '') ?? 0,
        int.tryParse(parts[5] ?? '') ?? 0,
      );
    }
  }
  return null;
}

/// 导入结果
class ImportResult {
  const ImportResult({
    required this.taskId,
    required this.status,
    required this.message,
    this.parsedTitle,
    this.parsedTime,
    this.parsedLocation,
    this.parsedTags = const [],
    this.parsedSummary,
    this.parsedEventType,
    this.createdItemId,
  });

  final int taskId;
  final String status;
  final String message;
  final String? parsedTitle;
  final String? parsedTime;
  final String? parsedLocation;
  final List<String> parsedTags;
  final String? parsedSummary;
  final String? parsedEventType;
  final int? createdItemId;

  bool get hasParsedData =>
      parsedTitle != null && parsedTitle!.isNotEmpty;

  factory ImportResult.fromJson(Map<String, Object?> json) {
    final event = json['event'] as Map<String, Object?>? ??
        json['parsedEvent'] as Map<String, Object?>? ??
        const {};
    final tagsRaw = (event['tags'] as List<Object?>? ?? const [])
        .whereType<String>()
        .toList();
    return ImportResult(
      taskId: (json['taskId'] as num?)?.toInt() ?? 0,
      status: json['status'] as String? ?? 'UNKNOWN',
      message: json['message'] as String? ?? '',
      parsedTitle: event['title'] as String? ??
          json['parsedTitle'] as String?,
      parsedTime: event['eventTime'] as String? ??
          event['time'] as String? ??
          json['parsedTime'] as String?,
      parsedLocation: event['location'] as String? ??
          json['parsedLocation'] as String?,
      parsedTags: tagsRaw.isEmpty
          ? (json['parsedTags'] as List<Object?>? ?? const [])
              .whereType<String>()
              .toList()
          : tagsRaw,
      parsedSummary: event['summary'] as String? ??
          event['aiSummary'] as String? ??
          json['parsedSummary'] as String?,
      parsedEventType: event['eventType'] as String? ??
          json['parsedEventType'] as String?,
      createdItemId: (json['createdItemId'] as num?)?.toInt() ??
          (event['id'] as num?)?.toInt(),
    );
  }
}

/// 导入任务记录
class ImportTaskItem {
  const ImportTaskItem({
    required this.taskId,
    required this.importType,
    required this.status,
    this.errorMessage,
    this.createdAt,
    this.finishedAt,
  });

  final int taskId;
  final String importType;
  final String status;
  final String? errorMessage;
  final DateTime? createdAt;
  final DateTime? finishedAt;

  factory ImportTaskItem.fromJson(Map<String, Object?> json) {
    return ImportTaskItem(
      taskId: (json['taskId'] as num?)?.toInt() ?? 0,
      importType: json['importType'] as String? ?? '',
      status: json['status'] as String? ?? 'UNKNOWN',
      errorMessage: json['errorMessage'] as String?,
      createdAt: json['createdAt'] == null
          ? null
          : DateTime.tryParse(json['createdAt'] as String),
      finishedAt: json['finishedAt'] == null
          ? null
          : DateTime.tryParse(json['finishedAt'] as String),
    );
  }
}

class ImportedEventItem {
  const ImportedEventItem({
    required this.id,
    required this.title,
    required this.summary,
    required this.eventType,
    required this.sourceType,
  });

  final int id;
  final String title;
  final String summary;
  final String eventType;
  final String sourceType;

  factory ImportedEventItem.fromJson(Map<String, Object?> json) =>
      ImportedEventItem(
        id: (json['id'] as num?)?.toInt() ?? 0,
        title: json['title'] as String? ?? '未命名课程信息',
        summary: json['summary'] as String? ?? '',
        eventType: json['eventType'] as String? ?? 'OTHER',
        sourceType: json['sourceType'] as String? ?? '',
      );
}

abstract class CampusApi {
  Future<LoginSession> login(String username, String password);

  Future<LoginSession> register(String username, String email, String password) =>
      Future.error(UnsupportedError('当前实现不支持注册'));

  Future<String?> forgotPassword(String account) =>
      Future.error(UnsupportedError('当前实现不支持找回密码'));

  Future<void> resetPassword(String token, String newPassword) =>
      Future.error(UnsupportedError('当前实现不支持重置密码'));

  Future<void> changePassword(
    String currentPassword,
    String newPassword,
    LoginSession session,
  ) =>
      Future.error(UnsupportedError('当前实现不支持修改密码'));

  /// 用本地缓存的会话尝试恢复登录；必要时刷新令牌。
  Future<LoginSession?> restoreSession(LoginSession session) =>
      Future.value(session);

  Future<void> logout(LoginSession session) => Future.value();

  Future<Map<String, Object?>> exportMyData(LoginSession session) =>
      Future.error(UnsupportedError('当前实现不支持数据导出'));

  Future<void> deleteMyAccount(String password, LoginSession session) =>
      Future.error(UnsupportedError('当前实现不支持账号注销'));

  Future<PrivacyStatus> fetchPrivacyStatus(LoginSession session) =>
      Future.value(const PrivacyStatus(policyVersion: '', retentionDays: 365, consents: {}));

  Future<PrivacyStatus> updateConsent(String type, bool granted, String policyVersion, LoginSession session) =>
      Future.error(UnsupportedError('当前实现不支持隐私授权'));

  Future<void> registerDevice(String deviceId, String platform, String? pushToken, LoginSession session) => Future.value();

  Future<List<NotificationDelivery>> fetchNotificationDeliveries(LoginSession session) =>
      Future.value(const []);

  Future<void> withdrawReminder(int reminderId, LoginSession session) =>
      Future.error(UnsupportedError('当前实现不支持撤回提醒'));

  Future<List<InformationItem>> fetchInformationFeed(LoginSession? session);

  Future<InformationFeedPage> fetchInformationPage(
    LoginSession? session, {
    String? cursor,
    int? cursorId,
    int? cursorSubscriptionMatch,
  }) async {
    final items = await fetchInformationFeed(session);
    return InformationFeedPage(
      items: items,
      hasMore: false,
      total: items.length,
    );
  }

  Future<InformationItem> fetchInformationDetail(int id, LoginSession? session);

  Future<InformationItem> updateReadStatus(
    int id,
    String readStatus,
    LoginSession session,
  );

  Future<void> confirmAction(int itemId, String title, LoginSession session) =>
      Future.error(UnsupportedError('当前实现不支持行动确认'));

  /// 粘贴文本导入
  Future<ImportResult> importText(String text, LoginSession session);

  /// 图片Base64导入
  Future<ImportResult> importImage(
      String base64, String? name, LoginSession session);

  /// 文件上传导入
  Future<ImportResult> importFile(
      List<int> bytes, String fileName, LoginSession session);

  /// 雨课堂JSON导入
  Future<ImportResult> importRainJson(
      String dataType, String rawJson, LoginSession session);

  /// 雨课堂Cookie导入
  Future<ImportResult> importRainCookie(
      String cookie, List<String> scopes, LoginSession session);

  /// 查询导入任务列表
  Future<List<ImportTaskItem>> fetchImportTasks(LoginSession session);

  /// 确认导入预览结果并提交
  Future<void> confirmImportPreview({
    required int taskId,
    required String title,
    String time = '',
    String location = '',
    String summary = '',
    List<String> tags = const [],
    String? eventType,
    required LoginSession session,
  });

  /// 当前用户可见的雨课堂私有事件
  Future<List<ImportedEventItem>> fetchRainEvents(LoginSession session);

  /// AI 对话
  Future<AiChatResult> aiChat(
      String sessionId, String message, LoginSession session);

  /// 搜索
  Future<SearchResult> search(String query, LoginSession session);

  /// 获取当前用户信息
  Future<UserProfile> fetchMe(LoginSession session);

  Future<UserProfile> updateProfile({
    String? college,
    String? major,
    String? grade,
    String? className,
    List<String> interestTags = const [],
    List<String> courseCodes = const [],
    required LoginSession session,
  }) =>
      Future.error(UnsupportedError('当前实现不支持编辑个人资料'));

  /// 获取用户统计
  Future<UserStats> fetchStats(LoginSession session);

  /// 获取收藏夹
  Future<List<InformationItem>> fetchFavorites(LoginSession session);

  /// 获取阅读历史
  Future<List<InformationItem>> fetchReadHistory(LoginSession session);

  /// 获取订阅列表
  Future<List<SubscriptionItem>> fetchSubscriptions(LoginSession session);

  /// 更新订阅状态
  Future<SubscriptionItem> updateSubscription(
      int sourceId, bool enabled, LoginSession session);

  /// 获取行动列表
  Future<List<ActionItem>> fetchActions(LoginSession session) =>
      Future.value(const []);

  Future<void> completeAction(int actionId, LoginSession session) =>
      Future.error(UnsupportedError('当前实现不支持完成待办'));

  Future<void> cancelAction(int actionId, LoginSession session) =>
      Future.error(UnsupportedError('当前实现不支持取消待办'));

  /// 获取提醒列表
  Future<List<ReminderItem>> fetchReminders(LoginSession session) =>
      Future.value(const []);

  /// 消除提醒
  Future<void> dismissReminder(int reminderId, LoginSession session) =>
      Future.error(UnsupportedError('当前实现不支持消除提醒'));

  /// 获取相关信息（去重融合）
  Future<List<RelatedItem>> fetchRelatedItems(
          int itemId, LoginSession session) =>
      Future.value(const []);

  /// 获取热门/趋势信息
  Future<List<TrendingItem>> fetchTrending(LoginSession session) =>
      Future.value(const []);

  /// 获取用户信息画像标签
  Future<UserProfileTags> fetchProfileTags(LoginSession session) =>
      Future.value(const UserProfileTags(tags: [], sensitivity: 0.5));

  /// 更新用户信息画像标签
  Future<UserProfileTags> updateProfileTags(
          List<String> tags, double sensitivity, LoginSession session) =>
      Future.value(const UserProfileTags(tags: [], sensitivity: 0.5));

  /// 获取 AI 日报摘要
  Future<DailyBriefing> fetchDailyBriefing(LoginSession session) =>
      Future.value(const DailyBriefing(summary: '', highlights: []));

  /// 带排序的 Feed
  Future<List<InformationItem>> fetchInformationFeedSorted(
          String sort, LoginSession? session) =>
      fetchInformationFeed(session);
}

class PrivacyStatus {
  const PrivacyStatus({required this.policyVersion, required this.retentionDays, required this.consents});
  final String policyVersion;
  final int retentionDays;
  final Map<String, bool> consents;

  factory PrivacyStatus.fromJson(Map<String, Object?> json) {
    final values = <String, bool>{};
    for (final raw in json['consents'] as List<Object?>? ?? const []) {
      final item = raw as Map<String, Object?>;
      values[item['consentType'] as String? ?? ''] = item['granted'] as bool? ?? false;
    }
    return PrivacyStatus(
      policyVersion: json['currentPolicyVersion'] as String? ?? '',
      retentionDays: (json['retentionDays'] as num?)?.toInt() ?? 365,
      consents: values,
    );
  }
}

class AiChatResult {
  const AiChatResult({required this.sessionId, required this.answer});
  final String sessionId;
  final String answer;
  factory AiChatResult.fromJson(Map<String, Object?> json) {
    return AiChatResult(
      sessionId: json['sessionId'] as String? ?? '',
      answer: json['answer'] as String? ?? '抱歉，暂时无法回答。',
    );
  }
}

class SearchResult {
  const SearchResult({required this.items, required this.total, this.message, this.mode = 'FILTER', this.fallback = false});
  final List<SearchResultItem> items;
  final int total;
  final String? message;
  final String mode;
  final bool fallback;
  factory SearchResult.fromJson(Map<String, Object?> json) {
    final list = json['items'] as List<Object?>? ?? const [];
    return SearchResult(
      items: list
          .cast<Map<String, Object?>>()
          .map(SearchResultItem.fromJson)
          .toList(),
      total: (json['total'] as num?)?.toInt() ?? 0,
      message: json['message'] as String?,
      mode: json['mode'] as String? ?? 'FILTER',
      fallback: json['fallback'] as bool? ?? false,
    );
  }
}

class SearchResultItem {
  const SearchResultItem(
      {required this.id,
      required this.title,
      required this.snippet,
      this.sourceName,
      this.eventType});
  final int id;
  final String title;
  final String snippet;
  final String? sourceName;
  final String? eventType;
  factory SearchResultItem.fromJson(Map<String, Object?> json) {
    return SearchResultItem(
      id: (json['id'] as num?)?.toInt() ?? 0,
      title: json['title'] as String? ?? '',
      snippet: json['snippet'] as String? ??
          json['summary'] as String? ??
          json['highlight'] as String? ??
          '',
      sourceName: json['sourceName'] as String?,
      eventType: json['eventType'] as String?,
    );
  }

  InformationItem toInformationItem() => InformationItem(
        id: id,
        title: title,
        sourceName: sourceName ?? '未知来源',
        preview: snippet,
        originalUrl: '',
        readStatus: 'NEW',
        itemStatus: 'ACTIVE',
        fetchedAt: DateTime.now(),
        eventType: eventType ?? 'OTHER',
      );
}

class UserProfile {
  const UserProfile(
      {required this.id,
      required this.username,
      this.nickname,
      this.email,
      this.phone,
      this.role,
      this.college,
      this.major,
      this.grade,
      this.className,
      this.interestTags = const [],
      this.courseCodes = const []});
  final int id;
  final String username;
  final String? nickname;
  final String? email;
  final String? phone;
  final String? role;
  final String? college;
  final String? major;
  final String? grade;
  final String? className;
  final List<String> interestTags;
  final List<String> courseCodes;

  List<String> get personalizationScopes => <String>{
        if (college?.trim().isNotEmpty == true) college!.trim(),
        if (major?.trim().isNotEmpty == true) major!.trim(),
        if (grade?.trim().isNotEmpty == true) grade!.trim(),
        if (className?.trim().isNotEmpty == true) className!.trim(),
        ...interestTags.map((value) => value.trim()).where((value) => value.isNotEmpty),
        ...courseCodes.map((value) => value.trim()).where((value) => value.isNotEmpty),
      }.toList();

  factory UserProfile.fromJson(Map<String, Object?> json) {
    final profile =
        (json['profile'] as Map?)?.cast<String, Object?>() ?? const {};
    return UserProfile(
      id: (json['id'] as num?)?.toInt() ?? 0,
      username: json['username'] as String? ?? '',
      nickname: json['nickname'] as String?,
      email: json['email'] as String?,
      phone: json['phone'] as String?,
      role: json['role'] as String?,
      college: profile['college'] as String?,
      major: profile['major'] as String?,
      grade: profile['grade'] as String?,
      className: profile['className'] as String?,
      interestTags: (profile['interestTags'] as List<Object?>? ?? const [])
          .whereType<String>()
          .toList(),
      courseCodes: (profile['courseCodes'] as List<Object?>? ?? const [])
          .whereType<String>()
          .toList(),
    );
  }
}

/// 用户统计
class UserStats {
  const UserStats(
      {required this.readCount,
      required this.favoriteCount,
      required this.subscriptionCount});
  final int readCount;
  final int favoriteCount;
  final int subscriptionCount;
  factory UserStats.fromJson(Map<String, Object?> json) {
    return UserStats(
      readCount: (json['readCount'] as num?)?.toInt() ?? 0,
      favoriteCount: (json['favoriteCount'] as num?)?.toInt() ?? 0,
      subscriptionCount: (json['subscriptionCount'] as num?)?.toInt() ?? 0,
    );
  }
}

/// 行动项
class ActionItem {
  const ActionItem({
    required this.id,
    required this.informationItemId,
    required this.title,
    this.dueAt,
    this.originalUrl,
    required this.status,
    this.createdAt,
    this.sourceTitle,
    this.sourceName,
    this.requiredMaterials = const [],
  });

  final int id;
  final int informationItemId;
  final String title;
  final DateTime? dueAt;
  final String? originalUrl;
  final String status;
  final DateTime? createdAt;
  final String? sourceTitle;
  final String? sourceName;
  final List<String> requiredMaterials;

  bool get isExpired => dueAt != null && dueAt!.isBefore(DateTime.now());
  bool get isDueSoon =>
      dueAt != null &&
      !isExpired &&
      dueAt!.difference(DateTime.now()).inDays <= 3;
  bool get isCompleted => status == 'COMPLETED';

  ActionItem copyWith({String? status}) => ActionItem(
        id: id,
        informationItemId: informationItemId,
        title: title,
        dueAt: dueAt,
        originalUrl: originalUrl,
        status: status ?? this.status,
        createdAt: createdAt,
        sourceTitle: sourceTitle,
        sourceName: sourceName,
        requiredMaterials: requiredMaterials,
      );

  factory ActionItem.fromJson(Map<String, Object?> json) {
    final materialsRaw =
        json['requiredMaterials'] as List<Object?>? ?? const [];
    return ActionItem(
      id: (json['id'] as num).toInt(),
      informationItemId: (json['informationItemId'] as num).toInt(),
      title: json['title'] as String? ?? '',
      dueAt: json['dueAt'] == null
          ? null
          : DateTime.tryParse(json['dueAt'] as String),
      originalUrl: json['originalUrl'] as String?,
      status: json['status'] as String? ?? 'CONFIRMED',
      createdAt: json['createdAt'] == null
          ? null
          : DateTime.tryParse(json['createdAt'] as String),
      sourceTitle: json['sourceTitle'] as String?,
      sourceName: json['sourceName'] as String?,
      requiredMaterials: materialsRaw.whereType<String>().toList(),
    );
  }
}

/// 提醒项
class ReminderItem {
  const ReminderItem({
    required this.id,
    required this.actionItemId,
    this.informationItemId,
    required this.actionTitle,
    this.sourceTitle,
    this.originalUrl,
    this.remindAt,
    this.dueAt,
    required this.status,
    this.sentAt,
  });

  final int id;
  final int actionItemId;
  final int? informationItemId;
  final String actionTitle;
  final String? sourceTitle;
  final String? originalUrl;
  final DateTime? remindAt;
  final DateTime? dueAt;
  final String status;
  final DateTime? sentAt;

  bool get isDismissed => status == 'DISMISSED';
  bool get isDue => status == 'DUE';
  bool get isExpired => dueAt != null && dueAt!.isBefore(DateTime.now());

  factory ReminderItem.fromJson(Map<String, Object?> json) {
    return ReminderItem(
      id: (json['id'] as num).toInt(),
      actionItemId: (json['actionItemId'] as num).toInt(),
      informationItemId: json['informationItemId'] != null
          ? (json['informationItemId'] as num).toInt()
          : null,
      actionTitle: json['actionTitle'] as String? ?? '',
      sourceTitle: json['sourceTitle'] as String?,
      originalUrl: json['originalUrl'] as String?,
      remindAt: json['remindAt'] == null
          ? null
          : DateTime.tryParse(json['remindAt'] as String),
      dueAt: json['dueAt'] == null
          ? null
          : DateTime.tryParse(json['dueAt'] as String),
      status: json['status'] as String? ?? 'PENDING',
      sentAt: json['sentAt'] == null
          ? null
          : DateTime.tryParse(json['sentAt'] as String),
    );
  }
}

/// 通知投递记录（站内/推送账本）
class NotificationDelivery {
  const NotificationDelivery({
    required this.id,
    required this.reminderId,
    required this.channel,
    required this.status,
    this.attemptCount = 0,
    this.lastError,
    this.sentAt,
    this.withdrawnAt,
    this.createdAt,
  });

  final int id;
  final int reminderId;
  final String channel;
  final String status;
  final int attemptCount;
  final String? lastError;
  final DateTime? sentAt;
  final DateTime? withdrawnAt;
  final DateTime? createdAt;

  bool get isUnreadInApp =>
      channel.toUpperCase() == 'IN_APP' &&
      (status == 'SENT' || status == 'PENDING' || status == 'RETRY');

  String get statusLabel {
    switch (status) {
      case 'SENT':
        return '已发送';
      case 'PENDING':
        return '待发送';
      case 'RETRY':
        return '重试中';
      case 'SENDING':
        return '发送中';
      case 'FAILED':
        return '失败';
      case 'WITHDRAWN':
        return '已撤回';
      default:
        return status;
    }
  }

  factory NotificationDelivery.fromJson(Map<String, Object?> json) {
    return NotificationDelivery(
      id: (json['id'] as num?)?.toInt() ?? 0,
      reminderId: (json['reminderId'] as num?)?.toInt() ?? 0,
      channel: json['channel'] as String? ?? 'IN_APP',
      status: json['status'] as String? ?? 'PENDING',
      attemptCount: (json['attemptCount'] as num?)?.toInt() ?? 0,
      lastError: json['lastError'] as String?,
      sentAt: json['sentAt'] == null
          ? null
          : DateTime.tryParse(json['sentAt'] as String),
      withdrawnAt: json['withdrawnAt'] == null
          ? null
          : DateTime.tryParse(json['withdrawnAt'] as String),
      createdAt: json['createdAt'] == null
          ? null
          : DateTime.tryParse(json['createdAt'] as String),
    );
  }
}

/// 订阅项
class SubscriptionItem {
  const SubscriptionItem({
    required this.sourceId,
    required this.sourceName,
    required this.sourceType,
    required this.enabled,
    this.subscribedAt,
  });
  final int sourceId;
  final String sourceName;
  final String sourceType;
  final bool enabled;
  final DateTime? subscribedAt;
  factory SubscriptionItem.fromJson(Map<String, Object?> json) {
    return SubscriptionItem(
      sourceId: (json['sourceId'] as num?)?.toInt() ?? 0,
      sourceName: json['sourceName'] as String? ?? '',
      sourceType: json['sourceType'] as String? ?? '',
      enabled: json['enabled'] as bool? ?? false,
      subscribedAt: json['subscribedAt'] == null
          ? null
          : DateTime.tryParse(json['subscribedAt'] as String),
    );
  }
}

/// 相关信息条目
class RelatedItem {
  const RelatedItem({
    required this.id,
    required this.title,
    required this.sourceName,
    required this.displayTime,
    this.fuseNote = '',
  });
  final int id;
  final String title;
  final String sourceName;
  final String displayTime;
  final String fuseNote;

  factory RelatedItem.fromJson(Map<String, Object?> json) => RelatedItem(
        id: (json['id'] as num?)?.toInt() ?? 0,
        title: json['title'] as String? ?? '',
        sourceName: json['sourceName'] as String? ?? '',
        displayTime: json['displayTime'] as String? ?? '',
        fuseNote: json['fuseNote'] as String? ?? '',
      );
}

/// 热门/趋势条目
class TrendingItem {
  const TrendingItem({
    required this.id,
    required this.rank,
    required this.title,
    required this.heatLabel,
  });
  final int id;
  final String rank;
  final String title;
  final String heatLabel;

  factory TrendingItem.fromJson(Map<String, Object?> json) => TrendingItem(
        id: (json['id'] as num?)?.toInt() ?? 0,
        rank: json['rank'] as String? ?? '',
        title: json['title'] as String? ?? '',
        heatLabel: json['heatLabel'] as String? ?? '',
      );
}

/// 用户画像标签
class UserProfileTags {
  const UserProfileTags({required this.tags, required this.sensitivity});
  final List<String> tags;
  final double sensitivity;

  factory UserProfileTags.fromJson(Map<String, Object?> json) =>
      UserProfileTags(
        tags: (json['tags'] as List<Object?>? ?? const [])
            .whereType<String>()
            .toList(),
        sensitivity: (json['sensitivity'] as num?)?.toDouble() ?? 0.5,
      );
}

/// AI 日报
class DailyBriefing {
  const DailyBriefing({required this.summary, required this.highlights});
  final String summary;
  final List<String> highlights;

  factory DailyBriefing.fromJson(Map<String, Object?> json) => DailyBriefing(
        summary: json['summary'] as String? ?? '',
        highlights: (json['highlights'] as List<Object?>? ?? const [])
            .whereType<String>()
            .toList(),
      );
}

CampusApi createCampusApi({
  void Function(LoginSession original, LoginSession updated)? onSessionRefreshed,
}) {
  throw UnsupportedError('当前平台暂不支持网络请求');
}
