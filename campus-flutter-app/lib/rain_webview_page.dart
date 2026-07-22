import 'dart:async';
import 'dart:collection';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app_theme.dart';
import 'information_api.dart';
import 'rain_sync_manager.dart';

class RainWebViewPage extends StatefulWidget {
  const RainWebViewPage({
    super.key,
    required this.api,
    required this.session,
  });

  final CampusApi api;
  final LoginSession session;

  @override
  State<RainWebViewPage> createState() => _RainWebViewPageState();
}

class _RainWebViewPageState extends State<RainWebViewPage> {
  static final WebUri _initialUrl =
      WebUri('https://www.yuketang.cn/v2/web/index');
  InAppWebViewController? _controller;
  WebViewEnvironment? _environment;
  Directory? _environmentDirectory;
  String? _bridgeScript;
  String? _error;
  String _currentUrl = _initialUrl.toString();
  double _progress = 0;
  bool _submitting = false;
  bool _closing = false;
  bool _autoStarted = false;
  bool _discovering = false;
  bool _saveLogin = false;
  static const _saveLoginKey = 'rain_save_login';

  @override
  void initState() {
    super.initState();
    RainSyncManager.instance.beginLogin();
    _initialize();
  }

  Future<void> _initialize() async {
    try {
      // 先读取用户偏好：是否记住登录状态。
      final prefs = await SharedPreferences.getInstance();
      final saveLogin = prefs.getBool(_saveLoginKey) ?? false;
      final script = await rootBundle.loadString('assets/rain_bridge.js');
      WebViewEnvironment? environment;
      Directory? environmentDirectory;
      if (!kIsWeb && defaultTargetPlatform == TargetPlatform.windows) {
        if (await WebViewEnvironment.getAvailableVersion() == null) {
          throw Exception('未检测到 Microsoft Edge WebView2 Runtime');
        }
        if (saveLogin) {
          // 持久化目录：Cookie 会随 WebView2 userData 自动保存到磁盘，
          // 下次打开同一目录即可恢复登录态，免重复输入账密。
          final supportDir = await getApplicationSupportDirectory();
          environmentDirectory = Directory('${supportDir.path}/rain-webview');
          if (!await environmentDirectory.exists()) {
            await environmentDirectory.create(recursive: true);
          }
        } else {
          environmentDirectory =
              await Directory.systemTemp.createTemp('campusmind-rain-');
        }
        environment = await WebViewEnvironment.create(
          settings: WebViewEnvironmentSettings(
            userDataFolder: environmentDirectory.path,
          ),
        );
      }
      if (!mounted) {
        await environment?.dispose();
        if (!saveLogin) {
          await environmentDirectory?.delete(recursive: true);
        }
        return;
      }
      setState(() {
        _saveLogin = saveLogin;
        _bridgeScript = script;
        _environment = environment;
        _environmentDirectory = environmentDirectory;
      });
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    }
  }

  Future<void> _toggleSaveLogin(bool value) async {
    setState(() => _saveLogin = value);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_saveLoginKey, value);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(value ? '已开启记住登录，下次可免重新登录' : '已关闭记住登录，关闭后将清除登录态')));
    }
  }

  bool _isAllowedUrl(WebUri? url) {
    if (url == null || url.toString() == 'about:blank') return true;
    final uri = Uri.tryParse(url.toString());
    if (uri == null || uri.scheme != 'https') return false;
    final host = uri.host.toLowerCase();
    return host == 'yuketang.cn' || host.endsWith('.yuketang.cn');
  }

  Object? _receiveItems(List<dynamic> arguments) {
    final changed = RainSyncManager.instance.capture(arguments);
    if (changed && mounted) {
      setState(() {});
      unawaited(_maybeAutoSync());
    }
    return changed;
  }

  Future<List<RainCourseTarget>> _courses() async {
    final value = await _controller?.evaluateJavascript(
      source: 'window.CampusMindRainBridge?.courses() || []',
    );
    if (value is! List) return const [];
    return value
        .whereType<Map>()
        .map((item) => (
              url: item['url']?.toString() ?? '',
              courseName: item['courseName']?.toString() ?? '',
              semester: item['semester']?.toString(),
            ))
        .where((item) =>
            item.courseName.isNotEmpty && _isAllowedUrl(WebUri(item.url)))
        .map((item) => RainCourseTarget(
              url: item.url,
              courseName: item.courseName,
              semester: item.semester,
            ))
        .take(100)
        .toList();
  }

  Future<void> _maybeAutoSync() async {
    if (_autoStarted || _submitting || _closing || _discovering) return;
    _discovering = true;
    try {
      var previousCount = -1;
      var stableCount = 0;
      var courses = <RainCourseTarget>[];
      for (var attempt = 0; attempt < 8; attempt++) {
        await Future<void>.delayed(const Duration(milliseconds: 500));
        await _scanPage();
        courses = await _courses();
        stableCount = courses.length == previousCount ? stableCount + 1 : 0;
        previousCount = courses.length;
        if (courses.isNotEmpty && stableCount >= 2) break;
      }
      if (_autoStarted || _submitting || _closing || courses.isEmpty) return;
      _autoStarted = true;
      await _startBackground(courses);
    } finally {
      _discovering = false;
    }
  }

  Future<void> _scanPage() async {
    try {
      await _controller?.evaluateJavascript(
        source: 'window.CampusMindRainBridge?.scan();',
      );
    } catch (_) {
      // 页面跳转期间扫描失败时，下一次 DOM 变化会自动重试。
    }
  }

  Future<void> _submit() async {
    if (_submitting) return;
    await _scanPage();
    await Future<void>.delayed(const Duration(milliseconds: 350));
    final courses = await _courses();
    if (courses.isEmpty) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('请先完成登录并等待课程列表加载')),
        );
      }
      return;
    }
    await _startBackground(courses);
  }

  Future<void> _startBackground(List<RainCourseTarget> courses) async {
    if (_submitting || _bridgeScript == null) return;
    setState(() => _submitting = true);
    try {
      await RainSyncManager.instance.start(
        api: widget.api,
        session: widget.session,
        courses: courses,
        bridgeScript: _bridgeScript!,
        environment: _environment,
        environmentDirectory: _environmentDirectory,
        persistCookies: _saveLogin,
      );
      _environment = null;
      _environmentDirectory = null;
      _closing = true;
      if (mounted) Navigator.of(context).pop(true);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('启动后台同步失败：$error')),
        );
      }
    } finally {
      if (mounted && !_closing) setState(() => _submitting = false);
    }
  }

  Future<void> _clearSession() async {
    // 用户选择“记住登录”时跳过清理，让 Cookie 随持久化目录留存。
    if (_saveLogin) return;
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

  Future<void> _close([bool? result]) async {
    if (_closing) return;
    _closing = true;
    await _clearSession();
    if (mounted) Navigator.of(context).pop(result);
  }

  Future<void> _disposeEnvironment() async {
    try {
      await _environment?.dispose();
    } catch (_) {}
    // 持久化目录不删除，临时目录清理。
    if (!_saveLogin) {
      try {
        await _environmentDirectory?.delete(recursive: true);
      } catch (_) {}
    }
  }

  @override
  void dispose() {
    unawaited(_disposeEnvironment());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) unawaited(_close());
      },
      child: Scaffold(
        backgroundColor: AppTheme.bg,
        appBar: AppBar(
          backgroundColor: AppTheme.bg,
          leading: IconButton(
            icon: const Icon(Icons.arrow_back_ios_new, size: 20),
            onPressed: _close,
          ),
          title: const Text('登录雨课堂并同步'),
          actions: [
            Padding(
              padding: const EdgeInsets.only(right: 14),
              child: Center(
                child: Text('已采集 ${RainSyncManager.instance.collected}',
                    style: const TextStyle(
                        color: AppTheme.brandInk, fontWeight: FontWeight.w700)),
              ),
            ),
          ],
        ),
        body: _buildBody(),
      ),
    );
  }

  Widget _buildBody() {
    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text('WebView 初始化失败：$_error', textAlign: TextAlign.center),
        ),
      );
    }
    if (_bridgeScript == null) {
      return const Center(child: CircularProgressIndicator());
    }
    return Column(
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.fromLTRB(14, 9, 14, 9),
          color: AppTheme.surface,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(Uri.tryParse(_currentUrl)?.host ?? _currentUrl,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                      fontSize: 12,
                      color: AppTheme.ink2,
                      fontWeight: FontWeight.w600)),
              const SizedBox(height: 3),
              const Text('请本人登录；打开课程或学习日志后自动采集，不读取 Cookie、Token 或密码',
                  style: TextStyle(fontSize: 11, color: AppTheme.muted)),
            ],
          ),
        ),
        if (_progress < 1) LinearProgressIndicator(value: _progress),
        Expanded(
          child: InAppWebView(
            webViewEnvironment: _environment,
            initialUrlRequest: URLRequest(url: _initialUrl),
            initialUserScripts: UnmodifiableListView([
              UserScript(
                source: _bridgeScript!,
                injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
              ),
            ]),
            initialSettings: InAppWebViewSettings(
              javaScriptEnabled: true,
              useShouldOverrideUrlLoading: true,
              isInspectable: false,
              domStorageEnabled: true,
              databaseEnabled: true,
              thirdPartyCookiesEnabled: false,
              supportMultipleWindows: false,
            ),
            onWebViewCreated: (controller) {
              _controller = controller;
              controller.addJavaScriptHandler(
                handlerName: 'rainItems',
                callback: _receiveItems,
              );
            },
            shouldOverrideUrlLoading: (controller, action) async {
              if (action.isForMainFrame == false) {
                return NavigationActionPolicy.ALLOW;
              }
              if (_isAllowedUrl(action.request.url)) {
                return NavigationActionPolicy.ALLOW;
              }
              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('已阻止跳转到非雨课堂域名')),
                );
              }
              return NavigationActionPolicy.CANCEL;
            },
            onLoadStart: (controller, url) {
              if (mounted && url != null) {
                setState(() {
                  _currentUrl = url.toString();
                  _progress = 0;
                });
              }
            },
            onUpdateVisitedHistory: (controller, url, _) {
              if (mounted && url != null) {
                setState(() => _currentUrl = url.toString());
              }
            },
            onLoadStop: (controller, url) {
              if (mounted) setState(() => _progress = 1);
              unawaited(_scanPage());
            },
            onProgressChanged: (controller, progress) {
              if (mounted) setState(() => _progress = progress / 100);
            },
          ),
        ),
        SafeArea(
          top: false,
          child: Container(
            padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
            decoration: const BoxDecoration(
              color: AppTheme.surface,
              border: Border(top: BorderSide(color: AppTheme.line)),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(
                  children: [
                    Icon(_saveLogin
                        ? Icons.lock_outline
                        : Icons.lock_open_outlined,
                        size: 16,
                        color: AppTheme.muted),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        _saveLogin
                            ? '记住登录状态已开启：Cookie 会保存到下次打开'
                            : '记住登录状态已关闭：关闭后清除登录态',
                        style: const TextStyle(
                            fontSize: 11, color: AppTheme.muted),
                      ),
                    ),
                    SizedBox(
                      height: 24,
                      child: Switch(
                        value: _saveLogin,
                        onChanged: _submitting ? null : _toggleSaveLogin,
                        activeTrackColor: AppTheme.brand.withValues(alpha: 0.4),
                        activeThumbColor: AppTheme.brand,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    IconButton(
                      tooltip: '后退',
                      onPressed: () => _controller?.goBack(),
                      icon: const Icon(Icons.arrow_back),
                    ),
                    IconButton(
                      tooltip: '刷新',
                      onPressed: () => _controller?.reload(),
                      icon: const Icon(Icons.refresh),
                    ),
                    IconButton(
                      tooltip: '采集当前页',
                      onPressed: _scanPage,
                      icon: const Icon(Icons.manage_search),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: FilledButton.icon(
                        onPressed: _submitting ? null : _submit,
                        icon: _submitting
                            ? const SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(
                                    strokeWidth: 2, color: Colors.white))
                            : const Icon(Icons.cloud_upload_outlined),
                        label: Text(
                            '开始后台同步 ${RainSyncManager.instance.collected} 条'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
