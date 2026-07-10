import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import 'information_api.dart';

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
          seedColor: const Color(0xFF3976A8),
          brightness: Brightness.light,
        ),
        scaffoldBackgroundColor: const Color(0xFFF7F6F0),
        fontFamily: 'Roboto',
        useMaterial3: true,
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFFF7F6F0),
          foregroundColor: Color(0xFF17212B),
          centerTitle: false,
          elevation: 0,
          scrolledUnderElevation: 0,
        ),
        cardTheme: CardThemeData(
          color: Colors.white,
          elevation: 0,
          margin: EdgeInsets.zero,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(8),
            side: const BorderSide(color: Color(0xFFE7E1D4)),
          ),
        ),
        filledButtonTheme: FilledButtonThemeData(
          style: FilledButton.styleFrom(
            backgroundColor: const Color(0xFF17212B),
            foregroundColor: Colors.white,
            minimumSize: const Size.fromHeight(48),
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
          ),
        ),
        outlinedButtonTheme: OutlinedButtonThemeData(
          style: OutlinedButton.styleFrom(
            foregroundColor: const Color(0xFF17212B),
            side: const BorderSide(color: Color(0xFFCCC3B2)),
            minimumSize: const Size.fromHeight(46),
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
          ),
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: Colors.white,
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: const BorderSide(color: Color(0xFFE1D9C8)),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: const BorderSide(color: Color(0xFFE1D9C8)),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: const BorderSide(color: Color(0xFF3976A8), width: 1.4),
          ),
        ),
      ),
      home: LoginPage(api: api),
    );
  }
}

class LoginPage extends StatefulWidget {
  const LoginPage({super.key, required this.api});

  final CampusApi api;

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _usernameController = TextEditingController(text: '123456');
  final _passwordController = TextEditingController(text: '123456');
  bool _obscurePassword = true;
  bool _isLoading = false;

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    if (_isLoading) return;
    setState(() => _isLoading = true);
    try {
      final session = await widget.api.login(
        _usernameController.text.trim(),
        _passwordController.text,
      );
      if (!mounted) return;
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(
          builder: (_) => CampusShell(api: widget.api, session: session),
        ),
      );
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('登录失败：$error')),
      );
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _fillDemoAccount() {
    _usernameController.text = '123456';
    _passwordController.text = '123456';
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('已填入本地演示账号')),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(22, 28, 22, 28),
          children: [
            const _BrandMark(),
            const SizedBox(height: 34),
            Text(
              '把校园信息\n收进一个清爽入口',
              style: Theme.of(context).textTheme.displaySmall?.copyWith(
                    color: const Color(0xFF17212B),
                    fontWeight: FontWeight.w800,
                    height: 1.08,
                  ),
            ),
            const SizedBox(height: 14),
            Text(
              '登录后查看最新抓取的信息、来源和原文追溯。',
              style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                    color: const Color(0xFF65717D),
                    height: 1.5,
                  ),
            ),
            const SizedBox(height: 34),
            TextField(
              controller: _usernameController,
              textInputAction: TextInputAction.next,
              decoration: const InputDecoration(
                labelText: '账号',
                prefixIcon: Icon(Icons.person_outline),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _passwordController,
              obscureText: _obscurePassword,
              onSubmitted: (_) => _login(),
              decoration: InputDecoration(
                labelText: '密码',
                prefixIcon: const Icon(Icons.lock_outline),
                suffixIcon: IconButton(
                  tooltip: _obscurePassword ? '显示密码' : '隐藏密码',
                  onPressed: () =>
                      setState(() => _obscurePassword = !_obscurePassword),
                  icon: Icon(_obscurePassword
                      ? Icons.visibility_outlined
                      : Icons.visibility_off_outlined),
                ),
              ),
            ),
            const SizedBox(height: 18),
            FilledButton.icon(
              onPressed: _isLoading ? null : _login,
              icon: _isLoading
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.login),
              label: const Text('登录'),
            ),
            const SizedBox(height: 10),
            OutlinedButton.icon(
              onPressed: _fillDemoAccount,
              icon: const Icon(Icons.badge_outlined),
              label: const Text('填入演示账号'),
            ),
          ],
        ),
      ),
    );
  }
}

class CampusShell extends StatefulWidget {
  const CampusShell({
    super.key,
    required this.api,
    required this.session,
  });

  final CampusApi api;
  final LoginSession session;

  @override
  State<CampusShell> createState() => _CampusShellState();
}

class _CampusShellState extends State<CampusShell> {
  final List<InformationItem> _items = [];
  Timer? _refreshTimer;
  int _tabIndex = 0;
  bool _loading = true;
  String? _errorMessage;
  FeedFilter _filter = FeedFilter.all;

  @override
  void initState() {
    super.initState();
    _refresh();
    _refreshTimer = Timer.periodic(
      const Duration(seconds: 5),
      (_) => _refresh(silent: true),
    );
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    super.dispose();
  }

  Future<void> _refresh({bool silent = false}) async {
    if (!silent) {
      setState(() {
        _loading = true;
        _errorMessage = null;
      });
    }
    try {
      final items = await widget.api.fetchInformationFeed(widget.session);
      if (!mounted) return;
      setState(() {
        _items
          ..clear()
          ..addAll(items);
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      if (silent) return;
      setState(() {
        _loading = false;
        _errorMessage = error.toString();
      });
    }
  }

  void _replaceItem(InformationItem item) {
    final index = _items.indexWhere((value) => value.id == item.id);
    setState(() {
      if (index == -1) {
        _items.insert(0, item);
      } else {
        _items[index] = item;
      }
    });
  }

  void _openDetail(InformationItem item) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => InformationDetailPage(
          api: widget.api,
          session: widget.session,
          seedItem: item,
          onItemChanged: _replaceItem,
        ),
      ),
    );
  }

  void _logout() {
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => LoginPage(api: widget.api)),
      (_) => false,
    );
  }

  List<InformationItem> get _visibleItems {
    return switch (_filter) {
      FeedFilter.unread =>
        _items.where((item) => item.readStatus == 'NEW').toList(),
      FeedFilter.favorites =>
        _items.where((item) => item.readStatus == 'FAVORITED').toList(),
      FeedFilter.all => List.of(_items),
    };
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      HomeTab(
        items: _visibleItems,
        allItems: _items,
        loading: _loading,
        errorMessage: _errorMessage,
        filter: _filter,
        onFilterChanged: (value) => setState(() => _filter = value),
        onRefresh: _refresh,
        onOpenDetail: _openDetail,
        onOpenSources: () => setState(() => _tabIndex = 1),
        onOpenProfile: () => setState(() => _tabIndex = 2),
      ),
      SourcesTab(
        items: _items,
        loading: _loading,
        onRefresh: _refresh,
        onOpenDetail: _openDetail,
      ),
      ProfileTab(
        session: widget.session,
        itemCount: _items.length,
        unreadCount: _items.where((item) => item.readStatus == 'NEW').length,
        onRefresh: _refresh,
        onLogout: _logout,
      ),
    ];

    return Scaffold(
      body: SafeArea(child: pages[_tabIndex]),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tabIndex,
        onDestinationSelected: (index) => setState(() => _tabIndex = index),
        backgroundColor: Colors.white,
        indicatorColor: const Color(0xFFE7F0F7),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.auto_awesome_mosaic_outlined),
            selectedIcon: Icon(Icons.auto_awesome_mosaic),
            label: '首页',
          ),
          NavigationDestination(
            icon: Icon(Icons.hub_outlined),
            selectedIcon: Icon(Icons.hub),
            label: '来源',
          ),
          NavigationDestination(
            icon: Icon(Icons.account_circle_outlined),
            selectedIcon: Icon(Icons.account_circle),
            label: '我的',
          ),
        ],
      ),
    );
  }
}

class HomeTab extends StatelessWidget {
  const HomeTab({
    super.key,
    required this.items,
    required this.allItems,
    required this.loading,
    required this.errorMessage,
    required this.filter,
    required this.onFilterChanged,
    required this.onRefresh,
    required this.onOpenDetail,
    required this.onOpenSources,
    required this.onOpenProfile,
  });

  final List<InformationItem> items;
  final List<InformationItem> allItems;
  final bool loading;
  final String? errorMessage;
  final FeedFilter filter;
  final ValueChanged<FeedFilter> onFilterChanged;
  final Future<void> Function() onRefresh;
  final ValueChanged<InformationItem> onOpenDetail;
  final VoidCallback onOpenSources;
  final VoidCallback onOpenProfile;

  @override
  Widget build(BuildContext context) {
    final unreadCount =
        allItems.where((item) => item.readStatus == 'NEW').length;
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(18, 14, 18, 28),
        children: [
          _TopBar(onRefresh: onRefresh, onOpenProfile: onOpenProfile),
          const SizedBox(height: 18),
          _HeroPanel(total: allItems.length, unread: unreadCount),
          const SizedBox(height: 14),
          GridView.count(
            crossAxisCount: 2,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            childAspectRatio: 1.36,
            crossAxisSpacing: 10,
            mainAxisSpacing: 10,
            children: [
              _ActionTile(
                icon: Icons.refresh,
                title: '刷新',
                value: '${allItems.length}',
                color: const Color(0xFFE8F3F6),
                onTap: onRefresh,
              ),
              _ActionTile(
                icon: Icons.mark_email_unread_outlined,
                title: '未读',
                value: '$unreadCount',
                color: const Color(0xFFF4EEE1),
                onTap: () => onFilterChanged(FeedFilter.unread),
              ),
              _ActionTile(
                icon: Icons.bookmark_outline,
                title: '收藏',
                value:
                    '${allItems.where((item) => item.readStatus == 'FAVORITED').length}',
                color: const Color(0xFFECE9F7),
                onTap: () => onFilterChanged(FeedFilter.favorites),
              ),
              _ActionTile(
                icon: Icons.travel_explore,
                title: '来源',
                value:
                    '${allItems.map((item) => item.sourceName).toSet().length}',
                color: const Color(0xFFEAF0E2),
                onTap: onOpenSources,
              ),
            ],
          ),
          const SizedBox(height: 16),
          _FilterBar(filter: filter, onChanged: onFilterChanged),
          const SizedBox(height: 14),
          if (loading)
            const _LoadingBlock()
          else if (errorMessage != null)
            _ErrorBlock(message: errorMessage!, onRetry: onRefresh)
          else if (items.isEmpty)
            const _EmptyBlock()
          else
            ...items.map(
              (item) => Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: _InformationCard(
                    item: item, onTap: () => onOpenDetail(item)),
              ),
            ),
        ],
      ),
    );
  }
}

class SourcesTab extends StatelessWidget {
  const SourcesTab({
    super.key,
    required this.items,
    required this.loading,
    required this.onRefresh,
    required this.onOpenDetail,
  });

  final List<InformationItem> items;
  final bool loading;
  final Future<void> Function() onRefresh;
  final ValueChanged<InformationItem> onOpenDetail;

  @override
  Widget build(BuildContext context) {
    final groups = <String, List<InformationItem>>{};
    for (final item in items) {
      groups.putIfAbsent(item.sourceName, () => []).add(item);
    }
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(18, 14, 18, 28),
        children: [
          _PageHeader(
            title: '信息来源',
            subtitle: '${groups.length} 个来源正在集中展示',
            actionIcon: Icons.refresh,
            onAction: onRefresh,
          ),
          const SizedBox(height: 16),
          if (loading)
            const _LoadingBlock()
          else if (groups.isEmpty)
            const _EmptyBlock()
          else
            ...groups.entries.map(
              (entry) => Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: _SourcePanel(
                  sourceName: entry.key,
                  items: entry.value,
                  onOpenDetail: onOpenDetail,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class ProfileTab extends StatelessWidget {
  const ProfileTab({
    super.key,
    required this.session,
    required this.itemCount,
    required this.unreadCount,
    required this.onRefresh,
    required this.onLogout,
  });

  final LoginSession session;
  final int itemCount;
  final int unreadCount;
  final Future<void> Function() onRefresh;
  final VoidCallback onLogout;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 14, 18, 28),
      children: [
        _PageHeader(
          title: '我的',
          subtitle: session.user.username,
          actionIcon: Icons.logout,
          onAction: onLogout,
        ),
        const SizedBox(height: 16),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const CircleAvatar(
                      radius: 28,
                      backgroundColor: Color(0xFFE7F0F7),
                      child:
                          Icon(Icons.school_outlined, color: Color(0xFF3976A8)),
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            session.user.username,
                            style: Theme.of(context)
                                .textTheme
                                .titleLarge
                                ?.copyWith(
                                  fontWeight: FontWeight.w800,
                                ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            _roleLabel(session.user.role),
                            style: Theme.of(context)
                                .textTheme
                                .bodyMedium
                                ?.copyWith(
                                  color: const Color(0xFF65717D),
                                ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                Row(
                  children: [
                    Expanded(
                        child: _MetricBox(label: '已获取', value: '$itemCount')),
                    const SizedBox(width: 10),
                    Expanded(
                        child: _MetricBox(label: '未读', value: '$unreadCount')),
                  ],
                ),
                const SizedBox(height: 16),
                _InfoLine(
                  icon: Icons.timer_outlined,
                  text: '会话有效至 ${_formatDateTime(session.expiresAt)}',
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 14),
        FilledButton.icon(
          onPressed: onRefresh,
          icon: const Icon(Icons.sync),
          label: const Text('刷新信息'),
        ),
        const SizedBox(height: 10),
        OutlinedButton.icon(
          onPressed: onLogout,
          icon: const Icon(Icons.logout),
          label: const Text('退出登录'),
        ),
      ],
    );
  }
}

class InformationDetailPage extends StatefulWidget {
  const InformationDetailPage({
    super.key,
    required this.api,
    required this.session,
    required this.seedItem,
    required this.onItemChanged,
  });

  final CampusApi api;
  final LoginSession session;
  final InformationItem seedItem;
  final ValueChanged<InformationItem> onItemChanged;

  @override
  State<InformationDetailPage> createState() => _InformationDetailPageState();
}

class _InformationDetailPageState extends State<InformationDetailPage> {
  late InformationItem _item = widget.seedItem;
  bool _loading = true;
  bool _updating = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _loadDetail();
  }

  Future<void> _loadDetail() async {
    setState(() {
      _loading = true;
      _errorMessage = null;
    });
    try {
      var item = await widget.api
          .fetchInformationDetail(widget.seedItem.id, widget.session);
      if (item.readStatus == 'NEW') {
        try {
          item = await widget.api.updateReadStatus(
              item.id, 'READ', widget.session);
        } catch (_) {}
      }
      if (!mounted) return;
      setState(() {
        _item = item;
        _loading = false;
      });
      widget.onItemChanged(item);
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _errorMessage = error.toString();
      });
    }
  }

  Future<void> _changeStatus(String status) async {
    if (_updating) return;
    setState(() => _updating = true);
    try {
      final item =
          await widget.api.updateReadStatus(_item.id, status, widget.session);
      if (!mounted) return;
      setState(() => _item = item);
      widget.onItemChanged(item);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(status == 'FAVORITED' ? '已收藏' : '已取消收藏')),
      );
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('操作失败：$error')),
      );
    } finally {
      if (mounted) setState(() => _updating = false);
    }
  }

  Future<void> _copyOriginalUrl() async {
    await Clipboard.setData(ClipboardData(text: _item.originalUrl));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('原文链接已复制')),
    );
  }

  Future<void> _openOriginalUrl() async {
    final uri = Uri.tryParse(_item.originalUrl);
    if (uri == null || !await launchUrl(uri, mode: LaunchMode.externalApplication)) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('无法打开原文链接')));
    }
  }

  String _cardValue(String key) {
    final value = _item.aiCard[key];
    if (value is List) return value.where((item) => item != null).join('、');
    return value?.toString() ?? '';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('信息详情'),
        actions: [
          IconButton(
            tooltip: '刷新详情',
            onPressed: _loadDetail,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _loadDetail,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(18, 8, 18, 28),
          children: [
            if (_loading) const LinearProgressIndicator(minHeight: 2),
            if (_errorMessage != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child:
                    _ErrorBlock(message: _errorMessage!, onRetry: _loadDetail),
              ),
            Text(
              _item.title,
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    color: const Color(0xFF17212B),
                    fontWeight: FontWeight.w800,
                    height: 1.18,
                  ),
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _MetaChip(icon: Icons.source_outlined, text: _item.sourceName),
                _MetaChip(icon: Icons.schedule, text: _item.displayTime),
                _MetaChip(
                    icon: Icons.verified_outlined,
                    text: _itemStatusLabel(_item.itemStatus)),
              ],
            ),
            const SizedBox(height: 18),
            if (_item.aiCard.isNotEmpty) ...[
              Text('智能精简', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
              const SizedBox(height: 8),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      SelectableText(_item.preview.isEmpty ? '暂未生成摘要' : _item.preview,
                        style: Theme.of(context).textTheme.bodyLarge?.copyWith(height: 1.65)),
                      for (final field in const [
                        ('报名开始', 'registrationStartTime'), ('报名截止', 'registrationDeadline'),
                        ('持续时间', 'eventDuration'), ('所需材料', 'requiredMaterials'),
                        ('参与方式', 'participationMethod'), ('组队要求', 'teamRequirement')
                      ])
                        if (_cardValue(field.$2).isNotEmpty)
                          Padding(
                            padding: const EdgeInsets.only(top: 10),
                            child: Text('${field.$1}：${_cardValue(field.$2)}'),
                          ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 14),
              Text('完整原文', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
              const SizedBox(height: 8),
            ],
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: SelectableText(
                  _item.detailContent.isEmpty ? (_item.preview.isEmpty ? '暂无正文内容' : _item.preview) : _item.detailContent,
                  style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                        height: 1.72,
                        color: const Color(0xFF27313B),
                      ),
                ),
              ),
            ),
            const SizedBox(height: 14),
            OutlinedButton.icon(
              onPressed: _updating
                  ? null
                  : () => _changeStatus(
                      _item.readStatus == 'FAVORITED' ? 'READ' : 'FAVORITED'),
              icon: Icon(_item.readStatus == 'FAVORITED'
                  ? Icons.bookmark
                  : Icons.bookmark_outline),
              label: Text(_item.readStatus == 'FAVORITED' ? '取消收藏' : '收藏'),
            ),
            const SizedBox(height: 10),
            OutlinedButton.icon(
              onPressed: _item.originalUrl.isEmpty ? null : _openOriginalUrl,
              icon: const Icon(Icons.open_in_new),
              label: const Text('查看原文'),
            ),
            const SizedBox(height: 10),
            OutlinedButton.icon(
              onPressed: _copyOriginalUrl,
              icon: const Icon(Icons.link),
              label: const Text('复制原文链接'),
            ),
            const SizedBox(height: 10),
            SelectableText(
              _item.originalUrl,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: const Color(0xFF3976A8),
                    height: 1.4,
                  ),
            ),
          ],
        ),
      ),
    );
  }
}

enum FeedFilter { all, unread, favorites }

class _BrandMark extends StatelessWidget {
  const _BrandMark();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            color: const Color(0xFF17212B),
            borderRadius: BorderRadius.circular(8),
          ),
          child: const Icon(Icons.radar, color: Colors.white),
        ),
        const SizedBox(width: 12),
        Text(
          'CampusMind',
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.w800,
                color: const Color(0xFF17212B),
              ),
        ),
      ],
    );
  }
}

class _TopBar extends StatelessWidget {
  const _TopBar({required this.onRefresh, required this.onOpenProfile});

  final Future<void> Function() onRefresh;
  final VoidCallback onOpenProfile;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const Expanded(child: _BrandMark()),
        IconButton(
          tooltip: '刷新',
          onPressed: onRefresh,
          icon: const Icon(Icons.refresh),
        ),
        IconButton(
          tooltip: '我的',
          onPressed: onOpenProfile,
          icon: const Icon(Icons.account_circle_outlined),
        ),
      ],
    );
  }
}

class _HeroPanel extends StatelessWidget {
  const _HeroPanel({required this.total, required this.unread});

  final int total;
  final int unread;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: const Color(0xFFFFFFFF),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: const Color(0xFFE7E1D4)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '今日信息集中站',
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w800,
                  color: const Color(0xFF17212B),
                  height: 1.12,
                ),
          ),
          const SizedBox(height: 10),
          Text(
            '已收集 $total 条信息，其中 $unread 条等待查看。',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: const Color(0xFF65717D),
                  height: 1.45,
                ),
          ),
        ],
      ),
    );
  }
}

class _ActionTile extends StatelessWidget {
  const _ActionTile({
    required this.icon,
    required this.title,
    required this.value,
    required this.color,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String value;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: color,
      borderRadius: BorderRadius.circular(8),
      child: InkWell(
        borderRadius: BorderRadius.circular(8),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Icon(icon, color: const Color(0xFF17212B)),
              Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Expanded(
                    child: Text(
                      title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                            color: const Color(0xFF17212B),
                            fontWeight: FontWeight.w800,
                          ),
                    ),
                  ),
                  Text(
                    value,
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.w900,
                          color: const Color(0xFF17212B),
                        ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _FilterBar extends StatelessWidget {
  const _FilterBar({required this.filter, required this.onChanged});

  final FeedFilter filter;
  final ValueChanged<FeedFilter> onChanged;

  @override
  Widget build(BuildContext context) {
    return SegmentedButton<FeedFilter>(
      selected: {filter},
      onSelectionChanged: (values) => onChanged(values.first),
      segments: const [
        ButtonSegment(
            value: FeedFilter.all,
            icon: Icon(Icons.layers_outlined),
            label: Text('全部')),
        ButtonSegment(
            value: FeedFilter.unread,
            icon: Icon(Icons.markunread_outlined),
            label: Text('未读')),
        ButtonSegment(
            value: FeedFilter.favorites,
            icon: Icon(Icons.bookmark_outline),
            label: Text('收藏')),
      ],
    );
  }
}

class _InformationCard extends StatelessWidget {
  const _InformationCard({required this.item, required this.onTap});

  final InformationItem item;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(8),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      item.sourceName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.labelLarge?.copyWith(
                            color: const Color(0xFF3976A8),
                            fontWeight: FontWeight.w700,
                          ),
                    ),
                  ),
                  _StatusBadge(text: item.readStatus),
                ],
              ),
              const SizedBox(height: 9),
              Text(
                item.title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w800,
                      height: 1.25,
                      color: const Color(0xFF17212B),
                    ),
              ),
              const SizedBox(height: 8),
              Text(
                item.preview,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: const Color(0xFF5B6670),
                      height: 1.45,
                    ),
              ),
              const SizedBox(height: 12),
              _InfoLine(icon: Icons.schedule, text: item.displayTime),
            ],
          ),
        ),
      ),
    );
  }
}

class _SourcePanel extends StatelessWidget {
  const _SourcePanel({
    required this.sourceName,
    required this.items,
    required this.onOpenDetail,
  });

  final String sourceName;
  final List<InformationItem> items;
  final ValueChanged<InformationItem> onOpenDetail;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ExpansionTile(
        tilePadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
        childrenPadding: const EdgeInsets.fromLTRB(14, 0, 14, 14),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        collapsedShape:
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        leading: const Icon(Icons.hub_outlined, color: Color(0xFF3976A8)),
        title: Text(
          sourceName,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(fontWeight: FontWeight.w800),
        ),
        subtitle: Text('${items.length} 条信息'),
        children: items
            .map(
              (item) => ListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(item.title,
                    maxLines: 2, overflow: TextOverflow.ellipsis),
                subtitle: Text(item.displayTime),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => onOpenDetail(item),
              ),
            )
            .toList(),
      ),
    );
  }
}

class _PageHeader extends StatelessWidget {
  const _PageHeader({
    required this.title,
    required this.subtitle,
    required this.actionIcon,
    required this.onAction,
  });

  final String title;
  final String subtitle;
  final IconData actionIcon;
  final VoidCallback onAction;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.w800,
                    ),
              ),
              const SizedBox(height: 4),
              Text(
                subtitle,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: const Color(0xFF65717D),
                    ),
              ),
            ],
          ),
        ),
        IconButton(
          tooltip: title,
          onPressed: onAction,
          icon: Icon(actionIcon),
        ),
      ],
    );
  }
}

class _MetricBox extends StatelessWidget {
  const _MetricBox({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFFF7F6F0),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: 4),
          Text(
            value,
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w900,
                ),
          ),
        ],
      ),
    );
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    final color = switch (text) {
      'NEW' => const Color(0xFFE8F3F6),
      'FAVORITED' => const Color(0xFFECE9F7),
      _ => const Color(0xFFEAF0E2),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        _readStatusLabel(text),
        style: Theme.of(context).textTheme.labelSmall?.copyWith(
              color: const Color(0xFF17212B),
              fontWeight: FontWeight.w800,
            ),
      ),
    );
  }
}

class _MetaChip extends StatelessWidget {
  const _MetaChip({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Chip(
      avatar: Icon(icon, size: 16),
      label: Text(text),
      backgroundColor: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: const BorderSide(color: Color(0xFFE1D9C8)),
      ),
    );
  }
}

class _InfoLine extends StatelessWidget {
  const _InfoLine({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 16, color: const Color(0xFF71808D)),
        const SizedBox(width: 6),
        Expanded(
          child: Text(
            text,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: const Color(0xFF71808D),
                ),
          ),
        ),
      ],
    );
  }
}

class _LoadingBlock extends StatelessWidget {
  const _LoadingBlock();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.symmetric(vertical: 34),
      child: Center(child: CircularProgressIndicator()),
    );
  }
}

class _EmptyBlock extends StatelessWidget {
  const _EmptyBlock();

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            const Icon(Icons.inbox_outlined,
                size: 38, color: Color(0xFF3976A8)),
            const SizedBox(height: 10),
            Text(
              '暂时没有新的信息',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w800,
                  ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorBlock extends StatelessWidget {
  const _ErrorBlock({required this.message, required this.onRetry});

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          children: [
            const Icon(Icons.cloud_off_outlined,
                size: 38, color: Color(0xFF9C5C2E)),
            const SizedBox(height: 10),
            Text(
              '信息暂时不可用',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w800,
                  ),
            ),
            const SizedBox(height: 6),
            Text(
              message,
              textAlign: TextAlign.center,
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: const Color(0xFF65717D),
                  ),
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: const Text('重试'),
            ),
          ],
        ),
      ),
    );
  }
}

String _formatDateTime(DateTime value) {
  return '${value.year.toString().padLeft(4, '0')}-'
      '${value.month.toString().padLeft(2, '0')}-'
      '${value.day.toString().padLeft(2, '0')} '
      '${value.hour.toString().padLeft(2, '0')}:'
      '${value.minute.toString().padLeft(2, '0')}';
}

String _readStatusLabel(String status) {
  return {'NEW': '未读', 'READ': '已读', 'FAVORITED': '已收藏'}[status] ?? status;
}

String _roleLabel(String role) {
  return {'ADMIN': '管理员', 'OPERATOR': '运营人员', 'STUDENT': '学生'}[role] ?? role;
}

String _itemStatusLabel(String status) {
  return {
        'ACTIVE': '正常展示',
        'UPDATED': '已更新',
        'OFFLINE': '已下线',
        'FAILED': '处理失败'
      }[status] ??
      status;
}
