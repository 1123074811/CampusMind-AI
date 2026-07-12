import 'dart:convert';
import 'dart:io';

import 'information_api_stub.dart';

export 'information_api_stub.dart'
    show CampusApi, CampusUser, InformationItem, LoginSession, ImportResult, ImportTaskItem, SessionExpiredException,
         AiChatResult, SearchResult, SearchResultItem, UserProfile;

const _apiBase = String.fromEnvironment(
  'CAMPUSMIND_API_BASE',
  defaultValue: 'http://localhost:8080',
);

CampusApi createCampusApi() => IoCampusApi(_apiBase);

class IoCampusApi implements CampusApi {
  IoCampusApi(this.baseUrl);

  final String baseUrl;

  @override
  Future<LoginSession> login(String username, String password) async {
    final root = await _request(
      'POST',
      '/api/v1/auth/login',
      body: {'username': username, 'password': password},
    );
    return LoginSession.fromJson(_data(root));
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

  Future<Map<String, Object?>> _request(
    String method,
    String path, {
    LoginSession? session,
    Map<String, Object?>? body,
  }) async {
    final client = HttpClient();
    try {
      final uri = Uri.parse('$baseUrl$path');
      final request = await switch (method) {
        'POST' => client.postUrl(uri),
        'PUT' => client.putUrl(uri),
        _ => client.getUrl(uri),
      };
      request.headers.set(HttpHeaders.acceptHeader, 'application/json');
      request.headers.set(HttpHeaders.contentTypeHeader, 'application/json; charset=utf-8');
      if (session != null) {
        request.headers.set(
          HttpHeaders.authorizationHeader,
          '${session.tokenType} ${session.accessToken}',
        );
        request.headers.set('X-User-Id', session.user.id.toString());
      }
      if (body != null) {
        request.add(utf8.encode(jsonEncode(body)));
      }
      final response = await request.close();
      final responseBody = await response.transform(utf8.decoder).join();
      if (response.statusCode == 401) {
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
    final client = HttpClient();
    try {
      final uri = Uri.parse('$baseUrl/api/v1/import/file');
      final request = await client.postUrl(uri);
      final boundary = '----CampusMindBoundary${DateTime.now().millisecondsSinceEpoch}';
      request.headers.set(HttpHeaders.contentTypeHeader,
          'multipart/form-data; boundary=$boundary');
      request.headers.set(
        HttpHeaders.authorizationHeader,
        '${session.tokenType} ${session.accessToken}',
      );
      request.headers.set('X-User-Id', session.user.id.toString());

      // 构建 multipart body
      final buffer = StringBuffer();
      buffer.write('--$boundary\r\n');
      buffer.write('Content-Disposition: form-data; name="file"; filename="$fileName"\r\n');
      buffer.write('Content-Type: application/octet-stream\r\n\r\n');
      final headerBytes = utf8.encode(buffer.toString());
      final footerBytes = utf8.encode('\r\n--$boundary--\r\n');

      request.add(headerBytes);
      request.add(bytes);
      request.add(footerBytes);

      final response = await request.close();
      final responseBody = await response.transform(utf8.decoder).join();
      if (response.statusCode == 401) {
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
  Future<AiChatResult> aiChat(String sessionId, String message, LoginSession session) async {
    final root = await _request(
      'POST',
      '/api/v1/ai/chat',
      session: session,
      body: {'sessionId': sessionId, 'message': message, 'usePersonalProfile': true},
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
}

Future<List<InformationItem>> fetchInformationFeed() {
  return createCampusApi().fetchInformationFeed(null);
}
