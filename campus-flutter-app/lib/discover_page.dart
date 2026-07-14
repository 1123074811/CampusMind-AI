import 'package:flutter/material.dart';
import 'app_theme.dart';
import 'information_api.dart';
import 'detail_page.dart';

class PrototypeDiscoverPage extends StatelessWidget {
  const PrototypeDiscoverPage(
      {super.key, this.onOpenImport, required this.api, required this.session});
  final VoidCallback? onOpenImport;
  final CampusApi api;
  final LoginSession session;

  void _showManageChannels(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('管理信息频道',
                  style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                      color: AppTheme.ink)),
              const SizedBox(height: 12),
              const Text('在「我的订阅」中可添加/移除频道、调整订阅优先级。',
                  style: TextStyle(
                      fontSize: 13, color: AppTheme.ink2, height: 1.5)),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: TextButton(
                  onPressed: () => Navigator.of(ctx).pop(),
                  child: const Text('知道了'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showMoreTrending(BuildContext context) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => CategoryFeedPage(
          label: '本周热门', query: '热门', api: api, session: session),
    ));
  }

  void _showAddSubscription(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('添加订阅源',
                  style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                      color: AppTheme.ink)),
              const SizedBox(height: 12),
              _AddSubItem(
                  icon: Icons.link,
                  label: '粘贴网址自动订阅',
                  desc: '输入校园网站 URL，AI 自动识别并订阅'),
              const SizedBox(height: 10),
              _AddSubItem(
                  icon: Icons.rss_feed,
                  label: 'RSS 订阅',
                  desc: '添加 RSS Feed 地址'),
              const SizedBox(height: 10),
              _AddSubItem(
                  icon: Icons.school, label: '雨课堂授权', desc: '导入雨课堂课程数据'),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: TextButton(
                  onPressed: () => Navigator.of(ctx).pop(),
                  child: const Text('取消'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 6, 20, 22),
      children: [
        // Header
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('发现',
                style: TextStyle(
                    fontSize: 13,
                    color: AppTheme.muted,
                    fontWeight: FontWeight.w500)),
            const SizedBox(height: 2),
            const Text('探索校园',
                style: TextStyle(
                    fontSize: 21,
                    fontWeight: FontWeight.w800,
                    color: AppTheme.ink)),
          ],
        ),
        const SizedBox(height: 16),
        // Search + Import
        Row(
          children: [
            Expanded(
              child: GestureDetector(
                onTap: () {
                  Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => _SearchPage(api: api, session: session),
                    ),
                  );
                },
                child: Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                  decoration: BoxDecoration(
                      color: AppTheme.surface,
                      borderRadius: BorderRadius.circular(13),
                      border: Border.all(color: AppTheme.line)),
                  child: const Row(
                    children: [
                      Icon(Icons.search, color: AppTheme.muted, size: 19),
                      SizedBox(width: 9),
                      Text('搜索通知、讲座、活动、失物…',
                          style:
                              TextStyle(fontSize: 13.5, color: AppTheme.muted)),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(width: 10),
            GestureDetector(
              onTap: onOpenImport,
              child: Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: AppTheme.brand,
                  borderRadius: BorderRadius.circular(13),
                  boxShadow: [
                    BoxShadow(
                        color: AppTheme.brand.withValues(alpha: 0.3),
                        blurRadius: 10,
                        offset: const Offset(0, 4))
                  ],
                ),
                child: const Icon(Icons.add, color: Colors.white, size: 22),
              ),
            ),
          ],
        ),
        const SizedBox(height: 20),
        _SectionTitle(
            title: '信息频道',
            action: '管理',
            onAction: () => _showManageChannels(context)),
        const SizedBox(height: 12),
        _CategoryGrid(api: api, session: session),
        const SizedBox(height: 20),
        _SectionTitle(
            title: '本周热门',
            action: '更多',
            onAction: () => _showMoreTrending(context)),
        const SizedBox(height: 12),
        _TrendingRow(api: api, session: session),
        const SizedBox(height: 20),
        _SectionTitle(
            title: '我的订阅源',
            action: '添加',
            onAction: () => _showAddSubscription(context)),
        const SizedBox(height: 12),
        _SubscriptionList(api: api, session: session),
        const SizedBox(height: 16),
      ],
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle({required this.title, this.action, this.onAction});
  final String title;
  final String? action;
  final VoidCallback? onAction;
  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(title,
            style: const TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w700,
                color: AppTheme.ink)),
        if (action != null)
          GestureDetector(
            onTap: onAction,
            child: Text(action!,
                style: const TextStyle(
                    fontSize: 12.5,
                    color: AppTheme.brandInk,
                    fontWeight: FontWeight.w600)),
          ),
      ],
    );
  }
}

class _AddSubItem extends StatelessWidget {
  const _AddSubItem(
      {required this.icon, required this.label, required this.desc});
  final IconData icon;
  final String label;
  final String desc;
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
          color: AppTheme.surface2, borderRadius: BorderRadius.circular(12)),
      child: Row(
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
                color: AppTheme.brandSoft,
                borderRadius: BorderRadius.circular(10)),
            child: Icon(icon, color: AppTheme.brandInk, size: 18),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label,
                    style: const TextStyle(
                        fontSize: 13.5,
                        fontWeight: FontWeight.w600,
                        color: AppTheme.ink)),
                Text(desc,
                    style:
                        const TextStyle(fontSize: 11, color: AppTheme.muted)),
              ],
            ),
          ),
          const Icon(Icons.chevron_right, color: AppTheme.muted, size: 18),
        ],
      ),
    );
  }
}

class _CategoryGrid extends StatelessWidget {
  const _CategoryGrid({required this.api, required this.session});
  final CampusApi api;
  final LoginSession session;

  static const _categories = [
    ('教务通知', '已订阅', Icons.format_list_bulleted, AppTheme.brand, '教务'),
    ('课程学术', '已订阅', Icons.article_outlined, AppTheme.info, '课程'),
    ('校园活动', '已订阅', Icons.add, AppTheme.accent, '活动'),
    ('实习招聘', '已订阅', Icons.person_outline, AppTheme.rose, '实习'),
  ];

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Row(
          children: [
            Expanded(child: _buildCard(context, _categories[0])),
            const SizedBox(width: 12),
            Expanded(child: _buildCard(context, _categories[1])),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(child: _buildCard(context, _categories[2])),
            const SizedBox(width: 12),
            Expanded(child: _buildCard(context, _categories[3])),
          ],
        ),
        const SizedBox(height: 12),
        GestureDetector(
          onTap: () => Navigator.of(context).push(MaterialPageRoute(
            builder: (_) => CategoryFeedPage(
                label: '失物招领 · 后勤服务',
                query: '失物招领 后勤',
                api: api,
                session: session),
          )),
          child: _WideCatCard(),
        ),
      ],
    );
  }

  Widget _buildCard(
      BuildContext context, (String, String, IconData, Color, String) cat) {
    return GestureDetector(
      onTap: () => Navigator.of(context).push(MaterialPageRoute(
        builder: (_) => CategoryFeedPage(
            label: cat.$1, query: cat.$5, api: api, session: session),
      )),
      child: _CatCard(name: cat.$1, desc: cat.$2, icon: cat.$3, color: cat.$4),
    );
  }
}

class _CatCard extends StatelessWidget {
  const _CatCard(
      {required this.name,
      required this.desc,
      required this.icon,
      required this.color});
  final String name;
  final String desc;
  final IconData icon;
  final Color color;
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(AppTheme.radiusSm),
          border: Border.all(color: AppTheme.line)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
                color: color, borderRadius: BorderRadius.circular(11)),
            child: Icon(icon, color: Colors.white, size: 20),
          ),
          const SizedBox(height: 10),
          Text(name,
              style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                  color: AppTheme.ink)),
          const SizedBox(height: 2),
          Text(desc,
              style: const TextStyle(fontSize: 11.5, color: AppTheme.muted)),
        ],
      ),
    );
  }
}

class _WideCatCard extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(AppTheme.radiusSm),
          border: Border.all(color: AppTheme.line)),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
                color: const Color(0xFF8B5CF6),
                borderRadius: BorderRadius.circular(11)),
            child: const Icon(Icons.access_time, color: Colors.white, size: 20),
          ),
          const SizedBox(width: 12),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('失物招领 · 后勤服务',
                    style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        color: AppTheme.ink)),
                SizedBox(height: 2),
                Text('实时更新 · 一键认领',
                    style: TextStyle(fontSize: 11.5, color: AppTheme.muted)),
              ],
            ),
          ),
          const Icon(Icons.chevron_right, color: AppTheme.muted),
        ],
      ),
    );
  }
}

class _TrendingRow extends StatefulWidget {
  const _TrendingRow({required this.api, required this.session});
  final CampusApi api;
  final LoginSession session;

  @override
  State<_TrendingRow> createState() => _TrendingRowState();
}

class _TrendingRowState extends State<_TrendingRow> {
  List<TrendingItem> _items = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final items = await widget.api.fetchTrending(widget.session);
      if (!mounted) return;
      setState(() {
        _items = items;
        _error = null;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _items = [];
        _error = '热门内容加载失败，请稍后重试';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const SizedBox(
        height: 100,
        child: Center(
            child: SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2))),
      );
    }
    if (_error != null || _items.isEmpty) {
      return SizedBox(
        height: 100,
        child: Center(
          child: Text(_error ?? '暂无热门内容',
              style: const TextStyle(color: AppTheme.muted)),
        ),
      );
    }
    final trends = _items;
    return SizedBox(
      height: 100,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: trends.length,
        separatorBuilder: (_, __) => const SizedBox(width: 10),
        itemBuilder: (ctx, i) {
          final t = trends[i];
          return GestureDetector(
            onTap: () {
              if (t.id > 0) {
                Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => CategoryFeedPage(
                      label: t.title,
                      query: t.title,
                      api: widget.api,
                      session: widget.session),
                ));
              }
            },
            child: Container(
              width: 152,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                  color: AppTheme.surface,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: AppTheme.line)),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(t.rank,
                      style: const TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w800,
                          color: AppTheme.accent)),
                  const SizedBox(height: 4),
                  Text(t.title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                          color: AppTheme.ink,
                          height: 1.4)),
                  const Spacer(),
                  Text(t.heatLabel,
                      style: const TextStyle(
                          fontSize: 11,
                          color: AppTheme.muted,
                          fontWeight: FontWeight.w600)),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _SubscriptionList extends StatefulWidget {
  const _SubscriptionList({required this.api, required this.session});
  final CampusApi api;
  final LoginSession session;

  @override
  State<_SubscriptionList> createState() => _SubscriptionListState();
}

class _SubscriptionListState extends State<_SubscriptionList> {
  List<SubscriptionItem> _items = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final items = await widget.api.fetchSubscriptions(widget.session);
      if (!mounted) return;
      setState(() {
        _items = items;
        _error = null;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _error = '订阅源加载失败，请重试';
        _loading = false;
      });
    }
  }

  Future<void> _toggle(int index) async {
    final item = _items[index];
    final newEnabled = !item.enabled;
    try {
      await widget.api
          .updateSubscription(item.sourceId, newEnabled, widget.session);
      if (!mounted) return;
      setState(() {
        _items[index] = SubscriptionItem(
          sourceId: item.sourceId,
          sourceName: item.sourceName,
          sourceType: item.sourceType,
          enabled: newEnabled,
          subscribedAt: item.subscribedAt,
        );
      });
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('订阅状态未改变：$error')));
    }
  }

  String _typeLabel(String type) {
    switch (type) {
      case 'PUBLIC_WEB':
        return '自动抓取';
      case 'RAIN_CLASSROOM':
        return '雨课堂';
      default:
        return type;
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 20),
        child: Center(
            child: SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2))),
      );
    }
    if (_error != null || _items.isEmpty) {
      return Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
            color: AppTheme.surface,
            borderRadius: BorderRadius.circular(AppTheme.radiusSm),
            border: Border.all(color: AppTheme.line)),
        child: Center(
            child: Text(_error ?? '暂无订阅源',
                style: const TextStyle(fontSize: 13, color: AppTheme.muted))),
      );
    }
    return Container(
      decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(AppTheme.radiusSm),
          border: Border.all(color: AppTheme.line)),
      child: Column(
        children: List.generate(_items.length, (i) {
          final item = _items[i];
          return Column(
            children: [
              Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 15, vertical: 13),
                child: Row(
                  children: [
                    Container(
                      width: 32,
                      height: 32,
                      decoration: BoxDecoration(
                          color: AppTheme.surface2,
                          borderRadius: BorderRadius.circular(9)),
                      child: const Icon(Icons.rss_feed,
                          color: AppTheme.brandInk, size: 16),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(item.sourceName,
                              style: const TextStyle(
                                  fontSize: 13.5,
                                  fontWeight: FontWeight.w600,
                                  color: AppTheme.ink)),
                          Text(_typeLabel(item.sourceType),
                              style: const TextStyle(
                                  fontSize: 11, color: AppTheme.muted)),
                        ],
                      ),
                    ),
                    GestureDetector(
                      onTap: () => _toggle(i),
                      child: AnimatedContainer(
                        duration: const Duration(milliseconds: 200),
                        width: 42,
                        height: 24,
                        padding: const EdgeInsets.all(3),
                        decoration: BoxDecoration(
                          color:
                              item.enabled ? AppTheme.brand : AppTheme.surface2,
                          borderRadius: BorderRadius.circular(99),
                        ),
                        child: AnimatedAlign(
                          duration: const Duration(milliseconds: 200),
                          alignment: item.enabled
                              ? Alignment.centerRight
                              : Alignment.centerLeft,
                          child: Container(
                              width: 18,
                              height: 18,
                              decoration: const BoxDecoration(
                                  color: Colors.white, shape: BoxShape.circle)),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              if (i < _items.length - 1)
                const Divider(height: 1, color: AppTheme.line),
            ],
          );
        }),
      ),
    );
  }
}

class _SearchPage extends StatefulWidget {
  const _SearchPage({required this.api, required this.session});
  final CampusApi api;
  final LoginSession session;
  @override
  State<_SearchPage> createState() => _SearchPageState();
}

class _SearchPageState extends State<_SearchPage> {
  final _ctrl = TextEditingController();
  List<SearchResultItem> _results = [];
  bool _loading = false;
  String? _error;
  int _total = 0;

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _doSearch() async {
    final q = _ctrl.text.trim();
    if (q.isEmpty) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = await widget.api.search(q, widget.session);
      if (!mounted) return;
      setState(() {
        _results = result.items;
        _total = result.total;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = '$e';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      body: SafeArea(
        child: Column(
          children: [
            // Top bar
            Container(
              padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
              decoration: BoxDecoration(
                  color: AppTheme.surface,
                  border: Border(bottom: BorderSide(color: AppTheme.line))),
              child: Row(
                children: [
                  GestureDetector(
                    onTap: () => Navigator.of(context).pop(),
                    child: const Icon(Icons.arrow_back_ios_new,
                        size: 18, color: AppTheme.ink2),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: TextField(
                      controller: _ctrl,
                      autofocus: true,
                      textInputAction: TextInputAction.search,
                      onSubmitted: (_) => _doSearch(),
                      style: const TextStyle(fontSize: 14, color: AppTheme.ink),
                      decoration: const InputDecoration(
                        border: InputBorder.none,
                        hintText: '搜索通知、讲座、活动…',
                        hintStyle:
                            TextStyle(fontSize: 14, color: AppTheme.muted),
                        isDense: true,
                      ),
                    ),
                  ),
                  GestureDetector(
                    onTap: _doSearch,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 12, vertical: 7),
                      decoration: BoxDecoration(
                          color: AppTheme.brand,
                          borderRadius: BorderRadius.circular(8)),
                      child: const Text('搜索',
                          style: TextStyle(
                              fontSize: 12.5,
                              fontWeight: FontWeight.w700,
                              color: Colors.white)),
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: _loading
                  ? const Center(
                      child: CircularProgressIndicator(color: AppTheme.brand))
                  : _error != null
                      ? Center(
                          child: Text('搜索失败：$_error',
                              style: const TextStyle(color: AppTheme.rose)))
                      : _results.isEmpty
                          ? const Center(
                              child: Text('输入关键词开始搜索',
                                  style: TextStyle(
                                      fontSize: 14, color: AppTheme.muted)))
                          : ListView.builder(
                              padding: const EdgeInsets.all(16),
                              itemCount: _results.length + 1,
                              itemBuilder: (ctx, i) {
                                if (i == 0) {
                                  return Padding(
                                    padding: const EdgeInsets.only(bottom: 12),
                                    child: Text('找到 $_total 条结果',
                                        style: const TextStyle(
                                            fontSize: 12.5,
                                            color: AppTheme.muted)),
                                  );
                                }
                                final item = _results[i - 1];
                                return Container(
                                  margin: const EdgeInsets.only(bottom: 12),
                                  padding: const EdgeInsets.all(14),
                                  decoration: BoxDecoration(
                                    color: AppTheme.surface,
                                    borderRadius: BorderRadius.circular(
                                        AppTheme.radiusSm),
                                    border: Border.all(color: AppTheme.line),
                                  ),
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      if (item.sourceName != null) ...[
                                        Container(
                                          padding: const EdgeInsets.symmetric(
                                              horizontal: 7, vertical: 2),
                                          decoration: BoxDecoration(
                                              color: AppTheme.brandSoft,
                                              borderRadius:
                                                  BorderRadius.circular(5)),
                                          child: Text(item.sourceName!,
                                              style: const TextStyle(
                                                  fontSize: 10,
                                                  fontWeight: FontWeight.w600,
                                                  color: AppTheme.brandInk)),
                                        ),
                                        const SizedBox(height: 6),
                                      ],
                                      Text(item.title,
                                          style: const TextStyle(
                                              fontSize: 14,
                                              fontWeight: FontWeight.w700,
                                              color: AppTheme.ink,
                                              height: 1.4)),
                                      if (item.snippet.isNotEmpty) ...[
                                        const SizedBox(height: 6),
                                        Text(item.snippet,
                                            maxLines: 3,
                                            overflow: TextOverflow.ellipsis,
                                            style: const TextStyle(
                                                fontSize: 12.5,
                                                color: AppTheme.ink2,
                                                height: 1.5)),
                                      ],
                                    ],
                                  ),
                                );
                              },
                            ),
            ),
          ],
        ),
      ),
    );
  }
}

// —————————————————————————————————————————
// 分类详情页：展示某个分类下的信息列表
// —————————————————————————————————————————

class CategoryFeedPage extends StatefulWidget {
  const CategoryFeedPage({
    super.key,
    required this.label,
    required this.query,
    required this.api,
    required this.session,
  });
  final String label;
  final String query;
  final CampusApi api;
  final LoginSession session;

  @override
  State<CategoryFeedPage> createState() => _CategoryFeedPageState();
}

class _CategoryFeedPageState extends State<CategoryFeedPage> {
  List<SearchResultItem> _results = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final results = await widget.api.search(widget.query, widget.session);
      if (!mounted) return;
      setState(() {
        _results = results.items;
        _error = null;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = '分类信息加载失败：$error';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      appBar: AppBar(
        backgroundColor: AppTheme.bg,
        elevation: 0,
        title: Text(widget.label,
            style: const TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w800,
                color: AppTheme.ink)),
        iconTheme: const IconThemeData(color: AppTheme.ink),
      ),
      body: _loading
          ? const Center(
              child: SizedBox(
                  width: 24,
                  height: 24,
                  child: CircularProgressIndicator(strokeWidth: 2)))
          : _error != null
              ? Center(
                  child: Text(_error!,
                      style: const TextStyle(color: AppTheme.muted)))
              : _results.isEmpty
                  ? const Center(
                      child: Text('暂无相关内容',
                          style:
                              TextStyle(fontSize: 14, color: AppTheme.muted)))
                  : ListView.separated(
                      padding: const EdgeInsets.fromLTRB(20, 10, 20, 24),
                      itemCount: _results.length,
                      separatorBuilder: (_, __) => const SizedBox(height: 12),
                      itemBuilder: (ctx, i) {
                        final r = _results[i];
                        return GestureDetector(
                          onTap: () =>
                              Navigator.of(context).push(MaterialPageRoute(
                            builder: (_) => PrototypeDetailPage(
                                item: r.toInformationItem(),
                                api: widget.api,
                                session: widget.session,
                                onItemChanged: (_) {}),
                          )),
                          child: Container(
                            padding: const EdgeInsets.all(14),
                            decoration: BoxDecoration(
                              color: AppTheme.surface,
                              borderRadius: BorderRadius.circular(14),
                              border: Border.all(color: AppTheme.line),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    Container(
                                      padding: const EdgeInsets.symmetric(
                                          horizontal: 7, vertical: 3),
                                      decoration: BoxDecoration(
                                          color: const Color(0xFFF3F4F6),
                                          borderRadius:
                                              BorderRadius.circular(4)),
                                      child: Text(r.sourceName ?? '未知来源',
                                          style: const TextStyle(
                                              fontSize: 10.5,
                                              fontWeight: FontWeight.w700,
                                              color: AppTheme.muted)),
                                    ),
                                    const Spacer(),
                                    if (r.eventType != null)
                                      Container(
                                        padding: const EdgeInsets.symmetric(
                                            horizontal: 7, vertical: 3),
                                        decoration: BoxDecoration(
                                            color: AppTheme.brandSoft,
                                            borderRadius:
                                                BorderRadius.circular(4)),
                                        child: Text(r.eventType!,
                                            style: const TextStyle(
                                                fontSize: 10.5,
                                                fontWeight: FontWeight.w700,
                                                color: AppTheme.brandInk)),
                                      ),
                                  ],
                                ),
                                const SizedBox(height: 8),
                                Text(r.title,
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(
                                        fontSize: 15,
                                        fontWeight: FontWeight.w700,
                                        color: AppTheme.ink,
                                        height: 1.4)),
                                const SizedBox(height: 6),
                                Text(r.snippet,
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(
                                        fontSize: 12.5,
                                        color: AppTheme.muted,
                                        height: 1.5)),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
    );
  }
}
