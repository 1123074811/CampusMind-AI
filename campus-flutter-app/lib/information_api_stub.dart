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
  });

  final String accessToken;
  final String tokenType;
  final DateTime expiresAt;
  final CampusUser user;

  factory LoginSession.fromJson(Map<String, Object?> json) {
    return LoginSession(
      accessToken: json['accessToken'] as String? ?? '',
      tokenType: json['tokenType'] as String? ?? 'Bearer',
      expiresAt: DateTime.tryParse(json['expiresAt'] as String? ?? '') ??
          DateTime.now().add(const Duration(hours: 2)),
      user: CampusUser.fromJson(
          json['user'] as Map<String, Object?>? ?? const {}),
    );
  }
}

class InformationItem {
  const InformationItem({
    required this.id,
    required this.title,
    required this.sourceName,
    required this.preview,
    this.detailContent = '',
    required this.originalUrl,
    required this.readStatus,
    required this.itemStatus,
    required this.fetchedAt,
    this.aiStatus = 'PENDING',
    this.eventType = 'OTHER',
    this.aiCard = const {},
    this.publishTime,
  });

  final int id;
  final String title;
  final String sourceName;
  final String preview;
  final String detailContent;
  final String originalUrl;
  final String readStatus;
  final String itemStatus;
  final DateTime fetchedAt;
  final String aiStatus;
  final String eventType;
  final Map<String, Object?> aiCard;
  final DateTime? publishTime;

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

  factory InformationItem.fromJson(Map<String, Object?> json) {
    return InformationItem(
      id: (json['id'] as num).toInt(),
      title: json['title'] as String? ?? '未命名信息',
      sourceName: json['sourceName'] as String? ?? '未知来源',
      preview: (json['preview'] ?? json['detailContent']) as String? ?? '',
      detailContent: json['detailContent'] as String? ?? '',
      originalUrl: (json['originalUrl'] ?? json['itemUrl']) as String? ?? '',
      readStatus: json['readStatus'] as String? ?? 'NEW',
      itemStatus: json['itemStatus'] as String? ?? 'ACTIVE',
      fetchedAt: DateTime.parse(json['fetchedAt'] as String),
      aiStatus: json['aiStatus'] as String? ?? 'PENDING',
      eventType: json['eventType'] as String? ?? 'OTHER',
      aiCard: (json['aiCard'] as Map?)?.cast<String, Object?>() ?? const {},
      publishTime: json['publishTime'] == null
          ? null
          : DateTime.tryParse(json['publishTime'] as String),
    );
  }

  InformationItem copyWith({
    String? preview,
    String? detailContent,
    String? readStatus,
    String? itemStatus,
  }) {
    return InformationItem(
      id: id,
      title: title,
      sourceName: sourceName,
      preview: preview ?? this.preview,
      detailContent: detailContent ?? this.detailContent,
      originalUrl: originalUrl,
      readStatus: readStatus ?? this.readStatus,
      itemStatus: itemStatus ?? this.itemStatus,
      fetchedAt: fetchedAt,
      aiStatus: aiStatus,
      eventType: eventType,
      aiCard: aiCard,
      publishTime: publishTime,
    );
  }
}

/// 导入结果
class ImportResult {
  const ImportResult({
    required this.taskId,
    required this.status,
    required this.message,
  });

  final int taskId;
  final String status;
  final String message;

  factory ImportResult.fromJson(Map<String, Object?> json) {
    return ImportResult(
      taskId: (json['taskId'] as num?)?.toInt() ?? 0,
      status: json['status'] as String? ?? 'UNKNOWN',
      message: json['message'] as String? ?? '',
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

abstract class CampusApi {
  Future<LoginSession> login(String username, String password);

  Future<List<InformationItem>> fetchInformationFeed(LoginSession? session);

  Future<InformationItem> fetchInformationDetail(int id, LoginSession? session);

  Future<InformationItem> updateReadStatus(
    int id,
    String readStatus,
    LoginSession session,
  );

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

  /// AI 对话
  Future<AiChatResult> aiChat(String sessionId, String message, LoginSession session);

  /// 搜索
  Future<SearchResult> search(String query, LoginSession session);

  /// 获取当前用户信息
  Future<UserProfile> fetchMe(LoginSession session);
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
  const SearchResult({required this.items, required this.total, this.message});
  final List<SearchResultItem> items;
  final int total;
  final String? message;
  factory SearchResult.fromJson(Map<String, Object?> json) {
    final list = json['items'] as List<Object?>? ?? const [];
    return SearchResult(
      items: list.cast<Map<String, Object?>>().map(SearchResultItem.fromJson).toList(),
      total: (json['total'] as num?)?.toInt() ?? 0,
      message: json['message'] as String?,
    );
  }
}

class SearchResultItem {
  const SearchResultItem({required this.id, required this.title, required this.snippet, this.sourceName, this.eventType});
  final int id;
  final String title;
  final String snippet;
  final String? sourceName;
  final String? eventType;
  factory SearchResultItem.fromJson(Map<String, Object?> json) {
    return SearchResultItem(
      id: (json['id'] as num?)?.toInt() ?? 0,
      title: json['title'] as String? ?? '',
      snippet: json['snippet'] as String? ?? json['highlight'] as String? ?? '',
      sourceName: json['sourceName'] as String?,
      eventType: json['eventType'] as String?,
    );
  }
}

class UserProfile {
  const UserProfile({required this.id, required this.username, this.nickname, this.email, this.role});
  final int id;
  final String username;
  final String? nickname;
  final String? email;
  final String? role;
  factory UserProfile.fromJson(Map<String, Object?> json) {
    return UserProfile(
      id: (json['id'] as num?)?.toInt() ?? 0,
      username: json['username'] as String? ?? '',
      nickname: json['nickname'] as String?,
      email: json['email'] as String?,
      role: json['role'] as String?,
    );
  }
}

CampusApi createCampusApi() {
  throw UnsupportedError('当前平台暂不支持网络请求');
}

Future<List<InformationItem>> fetchInformationFeed() {
  return createCampusApi().fetchInformationFeed(null);
}
