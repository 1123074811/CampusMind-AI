import 'dart:async';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart' show defaultTargetPlatform, debugPrint;

import 'information_api.dart';
import 'app_theme.dart';
import 'session_store.dart';
import 'splash_page.dart';
import 'login_page.dart';
import 'home_page.dart';
import 'discover_page.dart';
import 'assistant_page.dart';
import 'detail_page.dart';
import 'profile_page.dart';
import 'import_page.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const CampusMindApp());
}

/// 允许鼠标/触控板拖拽横向滚动（Windows 桌面默认仅触控可拖拽）。
class AppScrollBehavior extends MaterialScrollBehavior {
  const AppScrollBehavior();

  @override
  Set<PointerDeviceKind> get dragDevices => {
        PointerDeviceKind.touch,
        PointerDeviceKind.mouse,
        PointerDeviceKind.trackpad,
        PointerDeviceKind.stylus,
      };
}

class CampusMindApp extends StatefulWidget {
  const CampusMindApp({super.key, CampusApi? api, SessionStore? sessionStore})
      : _api = api,
        _sessionStore = sessionStore;
  final CampusApi? _api;
  final SessionStore? _sessionStore;

  @override
  State<CampusMindApp> createState() => _CampusMindAppState();
}

class _CampusMindAppState extends State<CampusMindApp> {
  late final SessionStore _store = widget._sessionStore ?? SessionStore();
  late final CampusApi _api = widget._api ??
      createCampusApi(
        onSessionRefreshed: (_, updated) {
          unawaited(_store.save(updated));
        },
      );

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'CampusMind',
      debugShowCheckedModeBanner: false,
      scrollBehavior: const AppScrollBehavior(),
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: AppTheme.brand,
          brightness: Brightness.light,
        ),
        scaffoldBackgroundColor: AppTheme.bg,
        useMaterial3: true,
      ),
      home: AppRoot(api: _api, sessionStore: _store),
    );
  }
}

class AppRoot extends StatefulWidget {
  const AppRoot({super.key, required this.api, required this.sessionStore});
  final CampusApi api;
  final SessionStore sessionStore;

  @override
  State<AppRoot> createState() => _AppRootState();
}

class _AppRootState extends State<AppRoot> {
  LoginSession? _session;
  bool _splashDone = false;
  bool _restoring = true;

  @override
  void initState() {
    super.initState();
    _restoreSession();
  }

  Future<void> _restoreSession() async {
    try {
      final cached = await widget.sessionStore.load();
      if (cached == null) {
        if (mounted) setState(() => _restoring = false);
        return;
      }
      final restored = await widget.api.restoreSession(cached);
      if (!mounted) return;
      if (restored == null) {
        await widget.sessionStore.clear();
        setState(() => _restoring = false);
        return;
      }
      await widget.sessionStore.save(restored);
      if (!mounted) return;
      setState(() {
        _session = restored;
        _restoring = false;
      });
      unawaited(_registerDevice(restored));
    } catch (error) {
      debugPrint('会话恢复失败：$error');
      await widget.sessionStore.clear();
      if (mounted) setState(() => _restoring = false);
    }
  }

  void _onSplashFinished() {
    if (!mounted) return;
    setState(() => _splashDone = true);
  }

  Future<void> _onLogin(LoginSession session) async {
    await widget.sessionStore.save(session);
    if (!mounted) return;
    setState(() => _session = session);
    unawaited(_registerDevice(session));
  }

  Future<void> _registerDevice(LoginSession session) async {
    try {
      await widget.api.registerDevice(
        'campus-app-${session.user.id}',
        defaultTargetPlatform.name,
        null,
        session,
      );
    } catch (error) {
      debugPrint('设备通知注册失败：$error');
    }
  }

  Future<void> _onLogout() async {
    final current = _session;
    if (current != null) {
      try {
        await widget.api.logout(current);
      } catch (error) {
        debugPrint('登出请求失败：$error');
      }
    }
    await widget.sessionStore.clear();
    if (!mounted) return;
    setState(() => _session = null);
  }

  void _onSessionExpired() {
    unawaited(_forceLogout(showMessage: true));
  }

  Future<void> _forceLogout({bool showMessage = false}) async {
    await widget.sessionStore.clear();
    if (!mounted) return;
    setState(() => _session = null);
    if (showMessage) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('登录已过期，请重新登录')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_splashDone) {
      return SplashPage(onFinished: _onSplashFinished);
    }
    if (_restoring) {
      return const Scaffold(
        backgroundColor: AppTheme.bg,
        body: Center(child: CircularProgressIndicator()),
      );
    }
    if (_session == null) {
      return PrototypeLoginPage(api: widget.api, onLogin: _onLogin);
    }
    return CampusShell(
      api: widget.api,
      session: _session!,
      onLogout: _onLogout,
      onSessionExpired: _onSessionExpired,
    );
  }
}

class CampusShell extends StatefulWidget {
  const CampusShell({
    super.key,
    required this.api,
    required this.session,
    required this.onLogout,
    this.onSessionExpired,
  });
  final CampusApi api;
  final LoginSession session;
  final Future<void> Function() onLogout;
  final VoidCallback? onSessionExpired;

  @override
  State<CampusShell> createState() => _CampusShellState();
}

class _CampusShellState extends State<CampusShell> {
  int _tabIndex = 0;
  final List<InformationItem> _items = [];
  String? _nextCursor;
  int? _nextCursorId;
  int? _nextSubscriptionMatch;
  int _total = 0;
  bool _hasMore = true;
  bool _loadingMore = false;
  String _feedMode = 'ALL';

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<void> _refresh() async {
    final requestedMode = _feedMode;
    setState(() {});
    try {
      final page = await widget.api.fetchInformationPage(
        widget.session,
        mode: requestedMode,
      );
      if (!mounted || requestedMode != _feedMode) return;
      setState(() {
        _items
          ..clear()
          ..addAll(page.items);
        _nextCursor = page.nextCursor;
        _nextCursorId = page.nextCursorId;
        _nextSubscriptionMatch = page.nextSubscriptionMatch;
        _hasMore = page.hasMore;
        _total = page.total;
      });
    } on SessionExpiredException {
      widget.onSessionExpired?.call();
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('信息流加载失败：$error')),
      );
    }
  }

  Future<void> _loadMore() async {
    if (_loadingMore || !_hasMore) return;
    final requestedMode = _feedMode;
    setState(() => _loadingMore = true);
    try {
      final page = await widget.api.fetchInformationPage(
        widget.session,
        cursor: _nextCursor,
        cursorId: _nextCursorId,
        cursorSubscriptionMatch: _nextSubscriptionMatch,
        mode: requestedMode,
      );
      if (!mounted || requestedMode != _feedMode) return;
      final knownIds = _items.map((item) => item.id).toSet();
      setState(() {
        _items.addAll(page.items.where((item) => knownIds.add(item.id)));
        _nextCursor = page.nextCursor;
        _nextCursorId = page.nextCursorId;
        _nextSubscriptionMatch = page.nextSubscriptionMatch;
        _hasMore = page.hasMore;
        _total = page.total;
      });
    } on SessionExpiredException {
      widget.onSessionExpired?.call();
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('加载更多失败：$error')),
        );
      }
    } finally {
      if (mounted) setState(() => _loadingMore = false);
    }
  }

  Future<void> _changeFeedMode(String mode) async {
    if (_feedMode == mode) return;
    setState(() {
      _feedMode = mode;
      _items.clear();
      _nextCursor = null;
      _nextCursorId = null;
      _nextSubscriptionMatch = null;
      _hasMore = true;
    });
    await _refresh();
  }

  void _replaceItem(InformationItem item) {
    final idx = _items.indexWhere((v) => v.id == item.id);
    setState(() {
      if (idx == -1) {
        _items.insert(0, item);
        _total++;
      } else {
        _items[idx] = item;
      }
    });
  }

  void _openDetail(InformationItem item) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => PrototypeDetailPage(
          item: item,
          api: widget.api,
          session: widget.session,
          onItemChanged: _replaceItem,
        ),
      ),
    );
  }

  void _openImport() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ImportPage(
          api: widget.api,
          session: widget.session,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      PrototypeHomePage(
        items: _items,
        onOpenDetail: _openDetail,
        onOpenImport: _openImport,
        userName: widget.session.user.username,
        api: widget.api,
        session: widget.session,
        total: _total,
        onItemUpdated: _replaceItem,
        onLoadMore: _loadMore,
        hasMore: _hasMore,
        loadingMore: _loadingMore,
        feedMode: _feedMode,
        onFeedModeChanged: _changeFeedMode,
      ),
      PrototypeDiscoverPage(
          onOpenImport: _openImport, api: widget.api, session: widget.session),
      PrototypeAssistantPage(api: widget.api, session: widget.session),
      PrototypeProfilePage(
        userName: widget.session.user.username,
        onLogout: widget.onLogout,
        api: widget.api,
        session: widget.session,
      ),
    ];

    return Scaffold(
      backgroundColor: AppTheme.bg,
      body: SafeArea(child: pages[_tabIndex]),
      bottomNavigationBar: _PillTabBar(
        index: _tabIndex,
        onChanged: (i) => setState(() => _tabIndex = i),
      ),
    );
  }
}

class _PillTabBar extends StatelessWidget {
  const _PillTabBar({required this.index, required this.onChanged});
  final int index;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) {
    final tabs = <(String, IconData, IconData)>[
      ('首页', Icons.home_outlined, Icons.home),
      ('发现', Icons.explore_outlined, Icons.explore),
      ('助手', Icons.auto_awesome_outlined, Icons.auto_awesome),
      ('我的', Icons.person_outline, Icons.person),
    ];
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 10, 18, 20),
      child: Container(
        height: 62,
        padding: const EdgeInsets.all(4),
        decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(36),
          border: Border.all(color: AppTheme.line),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.06),
              blurRadius: 12,
              offset: const Offset(0, -4),
            ),
          ],
        ),
        child: Row(
          children: List.generate(tabs.length, (i) {
            final t = tabs[i];
            final active = index == i;
            return Expanded(
              child: GestureDetector(
                onTap: () => onChanged(i),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  decoration: BoxDecoration(
                    color: active ? AppTheme.brand : Colors.transparent,
                    borderRadius: BorderRadius.circular(30),
                    boxShadow: active
                        ? [
                            BoxShadow(
                              color: AppTheme.brand.withValues(alpha: 0.4),
                              blurRadius: 18,
                              offset: const Offset(0, 8),
                            ),
                          ]
                        : null,
                  ),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(active ? t.$3 : t.$2,
                          size: 20,
                          color: active ? Colors.white : AppTheme.muted),
                      const SizedBox(height: 3),
                      Text(t.$1,
                          style: TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.w600,
                              color: active ? Colors.white : AppTheme.muted)),
                    ],
                  ),
                ),
              ),
            );
          }),
        ),
      ),
    );
  }
}
