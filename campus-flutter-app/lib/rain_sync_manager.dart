import 'dart:async';
import 'dart:collection';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import 'information_api.dart';

enum RainSyncPhase { idle, collecting, importing, success, failed }

class RainCourseTarget {
  const RainCourseTarget({
    required this.url,
    required this.courseName,
    this.semester,
  });

  final String url;
  final String courseName;
  final String? semester;
}

class RainSyncManager extends ChangeNotifier {
  RainSyncManager._();

  static final instance = RainSyncManager._();
  static const _types = {'COURSE', 'HOMEWORK', 'NOTICE', 'EXAM'};

  final _items = <String, Map<String, Object?>>{};
  final _submittedKeys = <String>{};
  final _courseContexts = <String, RainCourseTarget>{};
  HeadlessInAppWebView? _headless;
  InAppWebViewController? _controller;
  WebViewEnvironment? _environment;
  Directory? _environmentDirectory;
  bool _persistCookies = false;
  Completer<void>? _pageLoaded;
  String? _currentCourseName;
  String? _currentSemester;
  RainSyncPhase phase = RainSyncPhase.idle;
  int totalCourses = 0;
  int syncedCourses = 0;
  int success = 0;
  int skipped = 0;
  int failed = 0;
  int totalItems = 0;
  int processedItems = 0;
  String? error;

  bool get active =>
      phase == RainSyncPhase.collecting || phase == RainSyncPhase.importing;
  int get collected => _items.length;
  double? get progress {
    if (phase == RainSyncPhase.importing) {
      return totalItems == 0
          ? null
          : (processedItems / totalItems).clamp(0, 1).toDouble();
    }
    return totalCourses == 0
        ? null
        : (syncedCourses / totalCourses).clamp(0, 1).toDouble();
  }

  void beginLogin() {
    if (active) return;
    _items.clear();
    _submittedKeys.clear();
    _courseContexts.clear();
    phase = RainSyncPhase.idle;
    totalCourses = 0;
    syncedCourses = 0;
    success = 0;
    skipped = 0;
    failed = 0;
    totalItems = 0;
    processedItems = 0;
    error = null;
    notifyListeners();
  }

  String? _text(Object? value, {int maxLength = 2000}) {
    if (value is! String) return null;
    final text = value.replaceAll(RegExp(r'\s+'), ' ').trim();
    if (text.isEmpty || text == '[object Object]') return null;
    return text.length <= maxLength ? text : text.substring(0, maxLength);
  }

  String _contentDigest(String? content) {
    if (content == null || content.isEmpty) return '';
    var hash = 0;
    for (var i = 0; i < content.length; i++) {
      hash = (hash * 31 + content.codeUnitAt(i)) & 0x7fffffff;
    }
    return hash.toRadixString(16);
  }

  String? _safeSourceUrl(Object? value) {
    final text = _text(value, maxLength: 1000);
    final uri = text == null ? null : Uri.tryParse(text);
    if (uri == null || uri.scheme != 'https') return null;
    final host = uri.host.toLowerCase();
    if (host != 'yuketang.cn' && !host.endsWith('.yuketang.cn')) return null;
    return uri.replace(query: null, fragment: null).toString();
  }

  String? _courseKey(Object? value) {
    final uri = Uri.tryParse(value?.toString() ?? '');
    if (uri == null) return null;
    final id = uri.pathSegments.indexOf('studentLog');
    if (id < 0 || id + 1 >= uri.pathSegments.length) return null;
    return uri.pathSegments[id + 1];
  }

  bool capture(List<dynamic> arguments) {
    if (phase == RainSyncPhase.importing ||
        phase == RainSyncPhase.success ||
        phase == RainSyncPhase.failed) {
      return false;
    }
    if (arguments.isEmpty || arguments.first is! Map) return false;
    final payload = Map<Object?, Object?>.from(arguments.first as Map);
    final origin = Uri.tryParse(payload['origin']?.toString() ?? '');
    if (origin == null || origin.scheme != 'https') return false;
    final host = origin.host.toLowerCase();
    if (host != 'yuketang.cn' && !host.endsWith('.yuketang.cn')) return false;
    final rawItems = payload['items'];
    if (rawItems is! List) return false;
    final context = _courseContexts[_courseKey(payload['pageUrl'])];

    var changed = false;
    for (final raw in rawItems.take(500)) {
      if (raw is! Map) continue;
      final map = Map<Object?, Object?>.from(raw);
      final type = _text(map['dataType'], maxLength: 16)?.toUpperCase();
      if (type == null || !_types.contains(type)) continue;
      final capturedCourseName = _text(map['courseName'], maxLength: 200);
      if (type == 'COURSE' && capturedCourseName == null) continue;
      final courseName = type == 'COURSE'
          ? capturedCourseName
          : context?.courseName ?? capturedCourseName ?? _currentCourseName;
      final semester = type == 'COURSE'
          ? _text(map['semester'], maxLength: 64)
          : context?.semester ??
              _text(map['semester'], maxLength: 64) ??
              _currentSemester;
      final title = _text(map['title'], maxLength: 300);
      final content = _text(map['content']);
      if (title == null && content == null && courseName == null) continue;
      final item = <String, Object?>{
        'providerItemId': _text(map['providerItemId'], maxLength: 128),
        'dataType': type,
        'courseName': courseName,
        'semester': semester,
        'title': title ?? courseName,
        'content': content,
        'deadline': _text(map['deadline'], maxLength: 100),
        'publishedAt': _text(map['publishedAt'], maxLength: 100),
        'teacherName': _text(map['teacherName'], maxLength: 100),
        'sourceUrl': _safeSourceUrl(map['sourceUrl']),
      };
      // 学习日志里多条消息常缺 providerItemId/deadline，仅靠 title 去重会把
      // 同课程下同名或同模板的不同消息误并为一条（如"作业""通知"），
      // 导致一门课 10 条只采到 2-3 条。这里把内容摘要纳入 key。
      final contentDigest = _contentDigest(content);
      final key = [
        type,
        item['providerItemId'] ?? '',
        courseName ?? '',
        semester ?? '',
        item['title'] ?? '',
        item['deadline'] ?? '',
        item['publishedAt'] ?? '',
        contentDigest,
      ].join('|');
      if (!_items.containsKey(key) && _items.length < 5000) {
        _items[key] = item;
        totalItems = _items.length;
        changed = true;
      }
    }
    if (changed) notifyListeners();
    return changed;
  }

  Future<void> start({
    required CampusApi api,
    required LoginSession session,
    required List<RainCourseTarget> courses,
    required String bridgeScript,
    WebViewEnvironment? environment,
    Directory? environmentDirectory,
    bool persistCookies = false,
  }) async {
    if (active) return;
    phase = RainSyncPhase.collecting;
    totalCourses = courses.length;
    syncedCourses = 0;
    error = null;
    _environment = environment;
    _environmentDirectory = environmentDirectory;
    _persistCookies = persistCookies;
    _courseContexts
      ..clear()
      ..addEntries(courses.map(
          (course) => MapEntry(_courseKey(course.url) ?? course.url, course)));
    notifyListeners();

    final ready = Completer<void>();
    _headless = HeadlessInAppWebView(
      webViewEnvironment: environment,
      initialUrlRequest: URLRequest(url: WebUri('about:blank')),
      initialUserScripts: UnmodifiableListView([
        UserScript(
          source: bridgeScript,
          injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
        ),
      ]),
      initialSettings: InAppWebViewSettings(
        javaScriptEnabled: true,
        domStorageEnabled: true,
        databaseEnabled: true,
        thirdPartyCookiesEnabled: false,
      ),
      onWebViewCreated: (controller) {
        _controller = controller;
        controller.addJavaScriptHandler(
          handlerName: 'rainItems',
          callback: capture,
        );
        if (!ready.isCompleted) ready.complete();
      },
      onLoadStop: (_, __) {
        final loaded = _pageLoaded;
        if (loaded != null && !loaded.isCompleted) loaded.complete();
      },
    );
    await _headless!.run();
    await ready.future.timeout(const Duration(seconds: 10));
    unawaited(_run(api, session, courses));
  }

  Future<void> _run(CampusApi api, LoginSession session,
      List<RainCourseTarget> courses) async {
    try {
      for (var index = 0; index < courses.length; index++) {
        _currentCourseName = courses[index].courseName;
        _currentSemester = courses[index].semester;
        final loaded = Completer<void>();
        _pageLoaded = loaded;
        await _controller?.loadUrl(
          urlRequest: URLRequest(url: WebUri(courses[index].url)),
        );
        try {
          await loaded.future.timeout(const Duration(seconds: 8));
        } on TimeoutException {
          // 单门历史课程不可用时继续其余课程。
        }
        await Future<void>.delayed(const Duration(milliseconds: 500));
        var lastHeight = -1;
        var stableBottom = 0;
        for (var attempt = 0; attempt < 12; attempt++) {
          final state = await _controller?.evaluateJavascript(
            source: 'window.CampusMindRainBridge?.scrollForMore() || "1|0";',
          );
          final parts = state?.toString().split('|') ?? const [];
          final bottom = parts.isNotEmpty && parts.first == '1';
          final height = parts.length > 1 ? int.tryParse(parts[1]) ?? 0 : 0;
          stableBottom = bottom && height == lastHeight ? stableBottom + 1 : 0;
          lastHeight = height;
          await Future<void>.delayed(const Duration(milliseconds: 250));
          if (stableBottom >= 1) break;
        }
        await _controller?.evaluateJavascript(
          source: 'window.CampusMindRainBridge?.scan();',
        );
        syncedCourses = index + 1;
        notifyListeners();
        await _importPending(api, session);
      }

      await Future<void>.delayed(const Duration(milliseconds: 300));
      await _controller?.evaluateJavascript(
        source: 'window.CampusMindRainBridge?.scan();',
      );
      await Future<void>.delayed(const Duration(milliseconds: 250));
      await Future<void>.delayed(const Duration(milliseconds: 250));
      await _importPending(api, session);
      phase = RainSyncPhase.importing;
      notifyListeners();
      phase =
          success + skipped > 0 ? RainSyncPhase.success : RainSyncPhase.failed;
    } catch (exception) {
      error = exception.toString();
      phase = RainSyncPhase.failed;
    } finally {
      await _cleanup();
      notifyListeners();
    }
  }

  Future<void> _importPending(CampusApi api, LoginSession session) async {
    final pending = _items.entries
        .where((entry) => !_submittedKeys.contains(entry.key))
        .toList();
    const batchSize = 15;
    for (var offset = 0; offset < pending.length; offset += batchSize) {
      final entries = pending.sublist(
          offset,
          offset + batchSize > pending.length
              ? pending.length
              : offset + batchSize);
      var retryCount = 0;
      while (true) {
        try {
          final result = await api.importRainJson(
            'NOTICE',
            jsonEncode({
              'schemaVersion': 1,
              'provider': 'RAIN_CLASSROOM',
              'exportedAt': DateTime.now().toUtc().toIso8601String(),
              'items': entries.map((entry) => entry.value).toList(),
            }),
            session,
          );
          success += result.success;
          skipped += result.skipped;
          failed += result.failed;
          break;
        } catch (batchError) {
          final message = batchError.toString();
          if (retryCount < 2 &&
              (message.contains('频繁') ||
                  message.contains('429') ||
                  message.contains('RATE_LIMIT'))) {
            retryCount++;
            await Future<void>.delayed(Duration(seconds: 2 * retryCount));
            continue;
          }
          failed += entries.length;
          error = message;
          break;
        }
      }
      _submittedKeys.addAll(entries.map((entry) => entry.key));
      processedItems += entries.length;
      notifyListeners();
      if (offset + batchSize < pending.length) {
        await Future<void>.delayed(const Duration(milliseconds: 250));
      }
    }
  }

  Future<void> _cleanup() async {
    // 用户选择“记住登录”时跳过 Cookie / 缓存 / 环境目录清理，
    // 让登录态随持久化目录留存到下次同步。
    if (!_persistCookies) {
      try {
        await _controller?.evaluateJavascript(source: '''
          try { localStorage.clear(); sessionStorage.clear(); } catch (_) {}
          try { indexedDB.databases().then((dbs) => dbs.forEach((db) => db.name && indexedDB.deleteDatabase(db.name))); } catch (_) {}
        ''');
      } catch (_) {}
      try {
        await CookieManager.instance(webViewEnvironment: _environment)
            .deleteAllCookies();
      } catch (_) {}
      try {
        await InAppWebViewController.clearAllCache();
      } catch (_) {}
    }
    try {
      await _headless?.dispose();
    } catch (_) {}
    try {
      await _environment?.dispose();
    } catch (_) {}
    if (!_persistCookies) {
      try {
        await _environmentDirectory?.delete(recursive: true);
      } catch (_) {}
    }
    _headless = null;
    _controller = null;
    _environment = null;
    _environmentDirectory = null;
    _persistCookies = false;
    _currentCourseName = null;
    _currentSemester = null;
    _courseContexts.clear();
    _submittedKeys.clear();
  }
}
