import 'dart:convert';
import 'dart:io';

import 'information_api_stub.dart';

export 'information_api_stub.dart'
    show CampusApi, CampusUser, InformationItem, LoginSession;

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
      request.headers.set(HttpHeaders.contentTypeHeader, 'application/json');
      if (session != null) {
        request.headers.set(
          HttpHeaders.authorizationHeader,
          '${session.tokenType} ${session.accessToken}',
        );
        request.headers.set('X-User-Id', session.user.id.toString());
      }
      if (body != null) {
        request.write(jsonEncode(body));
      }
      final response = await request.close();
      final responseBody = await response.transform(utf8.decoder).join();
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
}

Future<List<InformationItem>> fetchInformationFeed() {
  return createCampusApi().fetchInformationFeed(null);
}
