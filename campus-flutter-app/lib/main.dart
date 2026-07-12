import 'dart:async';
import 'package:flutter/material.dart';

import 'information_api.dart';
import 'app_theme.dart';
import 'splash_page.dart';
import 'login_page.dart';
import 'home_page.dart';
import 'discover_page.dart';
import 'assistant_page.dart';
import 'detail_page.dart';
import 'profile_page.dart';
import 'import_page.dart';

void main() {
  runApp(const CampusMindApp());
}

class CampusMindApp extends StatelessWidget {
  const CampusMindApp({super.key, CampusApi? api}) : _api = api;
  final CampusApi? _api;

  @override
  Widget build(BuildContext context) {
    final api = _api ?? createCampusApi();
    return MaterialApp(
      title: 'CampusMind',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: AppTheme.brand,
          brightness: Brightness.light,
        ),
        scaffoldBackgroundColor: AppTheme.bg,
        useMaterial3: true,
      ),
      home: AppRoot(api: api),
    );
  }
}

class AppRoot extends StatefulWidget {
  const AppRoot({super.key, required this.api});
  final CampusApi api;

  @override
  State<AppRoot> createState() => _AppRootState();
}

class _AppRootState extends State<AppRoot> {
  LoginSession? _session;
  bool _splashDone = false;

  void _onSplashFinished() {
    if (!mounted) return;
    setState(() => _splashDone = true);
  }

  void _onLogin(LoginSession session) {
    setState(() => _session = session);
  }

  void _onLogout() {
    setState(() => _session = null);
  }

  @override
  Widget build(BuildContext context) {
    if (!_splashDone) {
      return SplashPage(onFinished: _onSplashFinished);
    }
    if (_session == null) {
      return PrototypeLoginPage(api: widget.api, onLogin: _onLogin);
    }
    return CampusShell(
      api: widget.api,
      session: _session!,
      onLogout: _onLogout,
    );
  }
}

class CampusShell extends StatefulWidget {
  const CampusShell({
    super.key,
    required this.api,
    required this.session,
    required this.onLogout,
  });
  final CampusApi api;
  final LoginSession session;
  final VoidCallback onLogout;

  @override
  State<CampusShell> createState() => _CampusShellState();
}

class _CampusShellState extends State<CampusShell> {
  int _tabIndex = 0;
  final List<InformationItem> _items = [];

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<void> _refresh() async {
    setState(() {});
    try {
      final items = await widget.api.fetchInformationFeed(widget.session);
      if (!mounted) return;
      setState(() {
        _items
          ..clear()
          ..addAll(items);
      });
    } catch (_) {
      if (!mounted) return;
    }
  }

  void _replaceItem(InformationItem item) {
    final idx = _items.indexWhere((v) => v.id == item.id);
    setState(() {
      if (idx == -1) {
        _items.insert(0, item);
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
      ),
      PrototypeDiscoverPage(onOpenImport: _openImport, api: widget.api, session: widget.session),
      PrototypeAssistantPage(api: widget.api, session: widget.session),
      PrototypeProfilePage(
        userName: widget.session.user.username,
        onLogout: widget.onLogout,
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
