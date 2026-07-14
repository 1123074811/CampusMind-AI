import 'dart:convert';
import 'dart:io';

import 'information_api_stub.dart';

export 'information_api_stub.dart'
    show
        CampusApi,
        CampusUser,
        InformationItem,
        LoginSession,
        ImportResult,
        ImportTaskItem,
        ImportedEventItem,
        SessionExpiredException,
        AiChatResult,
        SearchResult,
        SearchResultItem,
        UserProfile,
        UserStats,
        SubscriptionItem,
        ActionItem,
        ReminderItem,
        RelatedItem,
        TrendingItem,
        UserProfileTags,
        DailyBriefing;

const _apiBase = String.fromEnvironment(
  'CAMPUSMIND_API_BASE',
  defaultValue: 'http://localhost:8080',
);

CampusApi createCampusApi() => IoCampusApi(_apiBase);

class IoCampusApi implements CampusApi {
  IoCampusApi(this.baseUrl);

  final String baseUrl;
  final Map<LoginSession, LoginSession> _refreshedSessions = {};
  final Map<LoginSession, Future<void>> _refreshing = {};

  @override
  Future<LoginSession> login(String username, String password) async {
    final root = await _request(
      'POST',
      '/api/v1/auth/login',
      body: {'username': username, 'password': password},
    );
    return LoginSession.fromJson(_data(root));
  }

  LoginSession _effective(LoginSession session) =>
      _refreshedSessions[session] ?? session;

  Future<void> _refresh(LoginSession session) async {
    final inFlight = _refreshing[session];
    if (inFlight != null) return inFlight;
    final future = _performRefresh(session);
    _refreshing[session] = future;
    try {
      await future;
    } finally {
      _refreshing.remove(session);
    }
  }

  Future<void> _performRefresh(LoginSession session) async {
    final current = _effective(session);
    if (current.refreshToken.isEmpty ||
        (current.refreshExpiresAt?.isBefore(DateTime.now()) ?? false)) {
      throw const SessionExpiredException();
    }
    final root = await _request(
      'POST',
      '/api/v1/auth/refresh',
      body: {'refreshToken': current.refreshToken},
      allowRefresh: false,
    );
    _refreshedSessions[session] = LoginSession.fromJson(_data(root));
  }

  @override
  Future<void> logout(LoginSession session) async {
    final current = _effective(session);
    try {
      await _request(
        'POST',
        '/api/v1/auth/logout',
        session: session,
        body: {'refreshToken': current.refreshToken},
      );
    } finally {
      _refreshedSessions.remove(session);
    }
  }

  @override
  Future<List<InformationItem>> fetchInformationFeed(
      LoginSession? session) async {
    final root = await _request(
      'GET',
      '/api/v1/information/feed?size=30',
      session: session,
    );
    final data = _data(root);
    final items = data['items'] as List<Object?>? ?? const [];
    return items
        .cast<Map<String, Object?>>()
        .map(InformationItem.fromJson)
        .toList();
  }

  @override
  Future<InformationItem> fetchInformationDetail(
      int id, LoginSession? session) async {
    final root = await _request(
      'GET',
      '/api/v1/information/items/$id',
      session: session,
    );
    return InformationItem.fromJson(_data(root));
  }

  @override
  Future<InformationItem> updateReadStatus(
    int id,
    String readStatus,
    LoginSession session,
  ) async {
    final root = await _request(
      'PUT',
      '/api/v1/information/items/$id/read-status',
      session: session,
      body: {'readStatus': readStatus},
    );
    return InformationItem.fromJson(_data(root));
  }

  @override
  Future<void> confirmAction(
      int itemId, String title, LoginSession session) async {
    await _request(
      'POST',
      '/api/v1/information/items/$itemId/actions',
      session: session,
      body: {'title': title},
    );
  }

  Future<Map<String, Object?>> _request(
    String method,
    String path, {
    LoginSession? session,
    Map<String, Object?>? body,
    bool allowRefresh = true,
  }) async {
    final client = HttpClient();
    try {
      final uri = Uri.parse('$baseUrl$path');
      final request = await switch (method) {
        'POST' => client.postUrl(uri),
        'PUT' => client.putUrl(uri),
        'DELETE' => client.deleteUrl(uri),
        _ => client.getUrl(uri),
      };
      request.headers.set(HttpHeaders.acceptHeader, 'application/json');
      request.headers.set(
          HttpHeaders.contentTypeHeader, 'application/json; charset=utf-8');
      if (session != null) {
        final current = _effective(session);
        request.headers.set(
          HttpHeaders.authorizationHeader,
          '${current.tokenType} ${current.accessToken}',
        );
      }
      if (body != null) {
        request.add(utf8.encode(jsonEncode(body)));
      }
      final response = await request.close();
      final responseBody = await response.transform(utf8.decoder).join();
      if (response.statusCode == 401) {
        if (allowRefresh && session != null) {
          await _refresh(session);
          return _request(method, path,
              session: session, body: body, allowRefresh: false);
        }
        throw const SessionExpiredException();
      }
      final root = jsonDecode(responseBody.isEmpty ? '{}' : responseBody)
          as Map<String, Object?>;
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw HttpException(root['message'] as String? ?? '请求失败');
      }
      if (root['success'] == false) {
        throw HttpException(root['message'] as String? ?? '服务暂时不可用');
      }
      return root;
    } finally {
      client.close();
    }
  }

  Map<String, Object?> _data(Map<String, Object?> root) {
    return root['data'] as Map<String, Object?>? ?? const {};
  }

  @override
  Future<Map<String, Object?>> exportMyData(LoginSession session) async {
    final root =
        await _request('GET', '/api/v1/users/me/export', session: session);
    return _data(root);
  }

  @override
  Future<void> deleteMyAccount(String password, LoginSession session) async {
    await _request('DELETE', '/api/v1/users/me',
        session: session, body: {'password': password});
  }

  @override
  Future<ImportResult> importText(String text, LoginSession session) async {
    final root = await _request(
      'POST',
      '/api/v1/import/text',
      session: session,
      body: {'text': text},
    );
    return ImportResult.fromJson(_data(root));
  }

  @override
  Future<ImportResult> importImage(
      String base64, String? name, LoginSession session) async {
    final root = await _request(
      'POST',
      '/api/v1/import/image',
      session: session,
      body: {'imageBase64': base64, 'imageName': name},
    );
    return ImportResult.fromJson(_data(root));
  }

  @override
  Future<ImportResult> importFile(
      List<int> bytes, String fileName, LoginSession session) async {
    return _importFile(bytes, fileName, session, true);
  }

  Future<ImportResult> _importFile(List<int> bytes, String fileName,
      LoginSession session, bool allowRefresh) async {
    final client = HttpClient();
    try {
      final uri = Uri.parse('$baseUrl/api/v1/import/file');
      final request = await client.postUrl(uri);
      final boundary =
          '----CampusMindBoundary${DateTime.now().millisecondsSinceEpoch}';
      request.headers.set(HttpHeaders.contentTypeHeader,
          'multipart/form-data; boundary=$boundary');
      request.headers.set(
        HttpHeaders.authorizationHeader,
        '${_effective(session).tokenType} ${_effective(session).accessToken}',
      );

      // 构建 multipart body
      final buffer = StringBuffer();
      buffer.write('--$boundary\r\n');
      buffer.write(
          'Content-Disposition: form-data; name="file"; filename="$fileName"\r\n');
      buffer.write('Content-Type: application/octet-stream\r\n\r\n');
      final headerBytes = utf8.encode(buffer.toString());
      final footerBytes = utf8.encode('\r\n--$boundary--\r\n');

      request.add(headerBytes);
      request.add(bytes);
      request.add(footerBytes);

      final response = await request.close();
      final responseBody = await response.transform(utf8.decoder).join();
      if (response.statusCode == 401) {
        if (allowRefresh) {
          await _refresh(session);
          return _importFile(bytes, fileName, session, false);
        }
        throw const SessionExpiredException();
      }
      final root = jsonDecode(responseBody.isEmpty ? '{}' : responseBody)
          as Map<String, Object?>;
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw HttpException(root['message'] as String? ?? '请求失败');
      }
      if (root['success'] == false) {
        throw HttpException(root['message'] as String? ?? '服务暂时不可用');
      }
      return ImportResult.fromJson(_data(root));
    } finally {
      client.close();
    }
  }

  @override
  Future<ImportResult> importRainJson(
      String dataType, String rawJson, LoginSession session) async {
    final root = await _request(
      'POST',
      '/api/v1/import/rain/json',
      session: session,
      body: {'dataType': dataType, 'rawJson': rawJson},
    );
    return ImportResult.fromJson(_data(root));
  }

  @override
  Future<ImportResult> importRainCookie(
      String cookie, List<String> scopes, LoginSession session) async {
    final root = await _request(
      'POST',
      '/api/v1/import/rain/cookie',
      session: session,
      body: {
        'cookie': cookie,
        'importScopes': scopes,
        'agreeOneTimeUse': true,
      },
    );
    return ImportResult.fromJson(_data(root));
  }

  @override
  Future<List<ImportTaskItem>> fetchImportTasks(LoginSession session) async {
    final root = await _request(
      'GET',
      '/api/v1/import/tasks?size=20',
      session: session,
    );
    // 后端返回 data 为 List
    final list = root['data'];
    if (list is List) {
      return list
          .cast<Map<String, Object?>>()
          .map(ImportTaskItem.fromJson)
          .toList();
    }
    return const [];
  }

  @override
  Future<List<ImportedEventItem>> fetchRainEvents(LoginSession session) async {
    final root = await _request('GET', '/api/v1/events/search?page=1&size=100',
        session: session);
    final data = _data(root);
    final list = data['items'] as List<Object?>? ?? const [];
    return list
        .cast<Map<String, Object?>>()
        .map(ImportedEventItem.fromJson)
        .where((item) => item.sourceType == 'RAIN_CLASSROOM')
        .toList();
  }

  @override
  Future<AiChatResult> aiChat(
      String sessionId, String message, LoginSession session) async {
    final root = await _request(
      'POST',
      '/api/v1/ai/chat',
      session: session,
      body: {
        'sessionId': sessionId,
        'message': message,
        'usePersonalProfile': _isPersonalQuery(message),
      },
    );
    return AiChatResult.fromJson(_data(root));
  }

  @override
  Future<SearchResult> search(String query, LoginSession session) async {
    final root = await _request(
      'GET',
      '/api/v1/search?query=${Uri.encodeQueryComponent(query)}&usePersonalProfile=true',
      session: session,
    );
    return SearchResult.fromJson(_data(root));
  }

  @override
  Future<UserProfile> fetchMe(LoginSession session) async {
    final root = await _request(
      'GET',
      '/api/v1/users/me',
      session: session,
    );
    return UserProfile.fromJson(_data(root));
  }

  @override
  Future<UserStats> fetchStats(LoginSession session) async {
    final root = await _request(
      'GET',
      '/api/v1/information/stats',
      session: session,
    );
    return UserStats.fromJson(_data(root));
  }

  @override
  Future<List<InformationItem>> fetchFavorites(LoginSession session) async {
    final root = await _request(
      'GET',
      '/api/v1/information/favorites?size=30',
      session: session,
    );
    final data = _data(root);
    final items = data['items'] as List<Object?>? ?? const [];
    return items
        .cast<Map<String, Object?>>()
        .map(InformationItem.fromJson)
        .toList();
  }

  @override
  Future<List<InformationItem>> fetchReadHistory(LoginSession session) async {
    final root = await _request(
      'GET',
      '/api/v1/information/read-history?size=30',
      session: session,
    );
    final data = _data(root);
    final items = data['items'] as List<Object?>? ?? const [];
    return items
        .cast<Map<String, Object?>>()
        .map(InformationItem.fromJson)
        .toList();
  }

  @override
  Future<List<SubscriptionItem>> fetchSubscriptions(
      LoginSession session) async {
    final root = await _request(
      'GET',
      '/api/v1/information/subscriptions',
      session: session,
    );
    final list = root['data'];
    if (list is List) {
      return list
          .cast<Map<String, Object?>>()
          .map(SubscriptionItem.fromJson)
          .toList();
    }
    return const [];
  }

  @override
  Future<SubscriptionItem> updateSubscription(
      int sourceId, bool enabled, LoginSession session) async {
    final root = await _request(
      'PUT',
      '/api/v1/information/subscriptions/$sourceId',
      session: session,
      body: {'enabled': enabled},
    );
    return SubscriptionItem.fromJson(_data(root));
  }

  @override
  Future<List<ActionItem>> fetchActions(LoginSession session) async {
    final root = await _request(
      'GET',
      '/api/v1/information/actions',
      session: session,
    );
    final list = root['data'];
    if (list is List) {
      return list
          .cast<Map<String, Object?>>()
          .map(ActionItem.fromJson)
          .toList();
    }
    return const [];
  }

  @override
  Future<List<ReminderItem>> fetchReminders(LoginSession session) async {
    final root = await _request(
      'GET',
      '/api/v1/information/reminders',
      session: session,
    );
    final list = root['data'];
    if (list is List) {
      return list
          .cast<Map<String, Object?>>()
          .map(ReminderItem.fromJson)
          .toList();
    }
    return const [];
  }

  @override
  Future<void> dismissReminder(int reminderId, LoginSession session) async {
    await _request(
      'PUT',
      '/api/v1/information/reminders/$reminderId/dismiss',
      session: session,
    );
  }

  @override
  Future<List<RelatedItem>> fetchRelatedItems(
      int itemId, LoginSession session) async {
    final root = await _request(
      'GET',
      '/api/v1/information/items/$itemId/related',
      session: session,
    );
    final list = root['data'];
    if (list is List) {
      return list
          .cast<Map<String, Object?>>()
          .map(RelatedItem.fromJson)
          .toList();
    }
    return const [];
  }

  @override
  Future<List<TrendingItem>> fetchTrending(LoginSession session) async {
    final root = await _request(
      'GET',
      '/api/v1/information/trending?size=5',
      session: session,
    );
    final list = root['data'];
    if (list is List) {
      return list
          .cast<Map<String, Object?>>()
          .map(TrendingItem.fromJson)
          .toList();
    }
    return const [];
  }

  @override
  Future<UserProfileTags> fetchProfileTags(LoginSession session) async {
    final root = await _request(
      'GET',
      '/api/v1/users/profile-tags',
      session: session,
    );
    return UserProfileTags.fromJson(_data(root));
  }

  @override
  Future<UserProfileTags> updateProfileTags(
      List<String> tags, double sensitivity, LoginSession session) async {
    final root = await _request(
      'PUT',
      '/api/v1/users/profile-tags',
      session: session,
      body: {'tags': tags, 'sensitivity': sensitivity},
    );
    return UserProfileTags.fromJson(_data(root));
  }

  @override
  Future<DailyBriefing> fetchDailyBriefing(LoginSession session) async {
    final root = await _request(
      'GET',
      '/api/v1/ai/daily-briefing',
      session: session,
    );
    return DailyBriefing.fromJson(_data(root));
  }

  @override
  Future<List<InformationItem>> fetchInformationFeedSorted(
      String sort, LoginSession? session) async {
    final root = await _request(
      'GET',
      '/api/v1/information/feed?size=30&sort=${Uri.encodeQueryComponent(sort)}',
      session: session,
    );
    final data = _data(root);
    final items = data['items'] as List<Object?>? ?? const [];
    return items
        .cast<Map<String, Object?>>()
        .map(InformationItem.fromJson)
        .toList();
  }
}

/// 根据消息内容判断是否涉及个人日程/作业/课表等个性化查询。
bool _isPersonalQuery(String message) {
  const keywords = ['我的', '我本周', '我今天', '我明天', '我的课表', '我的作业', '我的考试', '我的日程'];
  return keywords.any(message.contains);
}
