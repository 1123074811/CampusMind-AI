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

abstract class CampusApi {
  Future<LoginSession> login(String username, String password);

  Future<List<InformationItem>> fetchInformationFeed(LoginSession? session);

  Future<InformationItem> fetchInformationDetail(int id, LoginSession? session);

  Future<InformationItem> updateReadStatus(
    int id,
    String readStatus,
    LoginSession session,
  );
}

CampusApi createCampusApi() {
  throw UnsupportedError('当前平台暂不支持网络请求');
}

Future<List<InformationItem>> fetchInformationFeed() {
  return createCampusApi().fetchInformationFeed(null);
}
