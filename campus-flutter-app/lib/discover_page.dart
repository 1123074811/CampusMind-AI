import 'package:flutter/material.dart';
import 'app_theme.dart';
import 'information_api.dart';

class PrototypeDiscoverPage extends StatelessWidget {
  const PrototypeDiscoverPage({super.key, this.onOpenImport, required this.api, required this.session});
  final VoidCallback? onOpenImport;
  final CampusApi api;
  final LoginSession session;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 6, 20, 22),
      children: [
        // Header
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('发现', style: TextStyle(fontSize: 13, color: AppTheme.muted, fontWeight: FontWeight.w500)),
            const SizedBox(height: 2),
            const Text('探索校园', style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800, color: AppTheme.ink)),
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
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                  decoration: BoxDecoration(color: AppTheme.surface, borderRadius: BorderRadius.circular(13), border: Border.all(color: AppTheme.line)),
                  child: const Row(
                    children: [
                      Icon(Icons.search, color: AppTheme.muted, size: 19),
                      SizedBox(width: 9),
                      Text('搜索通知、讲座、活动、失物…', style: TextStyle(fontSize: 13.5, color: AppTheme.muted)),
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
                  boxShadow: [BoxShadow(color: AppTheme.brand.withValues(alpha: 0.3), blurRadius: 10, offset: const Offset(0, 4))],
                ),
                child: const Icon(Icons.add, color: Colors.white, size: 22),
              ),
            ),
          ],
        ),
        const SizedBox(height: 20),
        _SectionTitle(title: '信息频道', action: '管理'),
        const SizedBox(height: 12),
        _CategoryGrid(),
        const SizedBox(height: 20),
        _SectionTitle(title: '本周热门', action: '更多'),
        const SizedBox(height: 12),
        _TrendingRow(),
        const SizedBox(height: 20),
        _SectionTitle(title: '我的订阅源', action: '添加'),
        const SizedBox(height: 12),
        _SubscriptionList(),
        const SizedBox(height: 16),
      ],
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle({required this.title, this.action});
  final String title;
  final String? action;
  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(title, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: AppTheme.ink)),
        if (action != null) Text(action!, style: const TextStyle(fontSize: 12.5, color: AppTheme.brandInk, fontWeight: FontWeight.w600)),
      ],
    );
  }
}

class _CategoryGrid extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final items = [
      ('教务通知', '326 条 · 已订阅', Icons.format_list_bulleted, AppTheme.brand),
      ('课程学术', '214 条 · 已订阅', Icons.article_outlined, AppTheme.info),
      ('校园活动', '158 条 · 已订阅', Icons.add, AppTheme.accent),
      ('实习招聘', '97 条 · 已订阅', Icons.person_outline, AppTheme.rose),
    ];
    return Column(
      children: [
        Row(
          children: [
            Expanded(child: _CatCard(name: items[0].$1, desc: items[0].$2, icon: items[0].$3, color: items[0].$4)),
            const SizedBox(width: 12),
            Expanded(child: _CatCard(name: items[1].$1, desc: items[1].$2, icon: items[1].$3, color: items[1].$4)),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(child: _CatCard(name: items[2].$1, desc: items[2].$2, icon: items[2].$3, color: items[2].$4)),
            const SizedBox(width: 12),
            Expanded(child: _CatCard(name: items[3].$1, desc: items[3].$2, icon: items[3].$3, color: items[3].$4)),
          ],
        ),
        const SizedBox(height: 12),
        _WideCatCard(),
      ],
    );
  }
}

class _CatCard extends StatelessWidget {
  const _CatCard({required this.name, required this.desc, required this.icon, required this.color});
  final String name;
  final String desc;
  final IconData icon;
  final Color color;
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(color: AppTheme.surface, borderRadius: BorderRadius.circular(AppTheme.radiusSm), border: Border.all(color: AppTheme.line)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 38, height: 38,
            decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(11)),
            child: Icon(icon, color: Colors.white, size: 20),
          ),
          const SizedBox(height: 10),
          Text(name, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: AppTheme.ink)),
          const SizedBox(height: 2),
          Text(desc, style: const TextStyle(fontSize: 11.5, color: AppTheme.muted)),
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
      decoration: BoxDecoration(color: AppTheme.surface, borderRadius: BorderRadius.circular(AppTheme.radiusSm), border: Border.all(color: AppTheme.line)),
      child: Row(
        children: [
          Container(
            width: 38, height: 38,
            decoration: BoxDecoration(color: const Color(0xFF8B5CF6), borderRadius: BorderRadius.circular(11)),
            child: const Icon(Icons.access_time, color: Colors.white, size: 20),
          ),
          const SizedBox(width: 12),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('失物招领 · 后勤服务', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: AppTheme.ink)),
                SizedBox(height: 2),
                Text('实时更新 · 一键认领', style: TextStyle(fontSize: 11.5, color: AppTheme.muted)),
              ],
            ),
          ),
          const Icon(Icons.chevron_right, color: AppTheme.muted),
        ],
      ),
    );
  }
}

class _TrendingRow extends StatelessWidget {
  final _trends = const [
    ('#1', '选课系统维护通知', '热度 2.4k'),
    ('#2', 'AI 前沿讲座报名', '热度 1.8k'),
    ('#3', '校园马拉松路线', '热度 1.2k'),
  ];
  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 100,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: _trends.length,
        separatorBuilder: (_, __) => const SizedBox(width: 10),
        itemBuilder: (ctx, i) {
          final t = _trends[i];
          return Container(
            width: 152,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(color: AppTheme.surface, borderRadius: BorderRadius.circular(14), border: Border.all(color: AppTheme.line)),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(t.$1, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800, color: AppTheme.accent)),
                const SizedBox(height: 4),
                Text(t.$2, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: AppTheme.ink, height: 1.4)),
                const Spacer(),
                Text(t.$3, style: const TextStyle(fontSize: 11, color: AppTheme.muted, fontWeight: FontWeight.w600)),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _SubscriptionList extends StatefulWidget {
  @override
  State<_SubscriptionList> createState() => _SubscriptionListState();
}

class _SubscriptionListState extends State<_SubscriptionList> {
  final _sources = [
    ('教务处', '官方通知 · 自动抓取', Icons.format_list_bulleted, true),
    ('学生会', '活动与社团', Icons.person_outline, true),
    ('图书馆', '座位与资源', Icons.article_outlined, false),
  ];
  late List<bool> _on;

  @override
  void initState() {
    super.initState();
    _on = _sources.map((e) => e.$4).toList();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(color: AppTheme.surface, borderRadius: BorderRadius.circular(AppTheme.radiusSm), border: Border.all(color: AppTheme.line)),
      child: Column(
        children: List.generate(_sources.length, (i) {
          final s = _sources[i];
          return Column(
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 13),
                child: Row(
                  children: [
                    Container(
                      width: 32, height: 32,
                      decoration: BoxDecoration(color: AppTheme.surface2, borderRadius: BorderRadius.circular(9)),
                      child: Icon(s.$3, color: AppTheme.brandInk, size: 16),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(s.$1, style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: AppTheme.ink)),
                          Text(s.$2, style: const TextStyle(fontSize: 11, color: AppTheme.muted)),
                        ],
                      ),
                    ),
                    GestureDetector(
                      onTap: () => setState(() => _on[i] = !_on[i]),
                      child: AnimatedContainer(
                        duration: const Duration(milliseconds: 200),
                        width: 42, height: 24,
                        padding: const EdgeInsets.all(3),
                        decoration: BoxDecoration(
                          color: _on[i] ? AppTheme.brand : AppTheme.surface2,
                          borderRadius: BorderRadius.circular(99),
                        ),
                        child: AnimatedAlign(
                          duration: const Duration(milliseconds: 200),
                          alignment: _on[i] ? Alignment.centerRight : Alignment.centerLeft,
                          child: Container(width: 18, height: 18, decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle)),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              if (i < _sources.length - 1) const Divider(height: 1, color: AppTheme.line),
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
  void dispose() { _ctrl.dispose(); super.dispose(); }

  Future<void> _doSearch() async {
    final q = _ctrl.text.trim();
    if (q.isEmpty) return;
    setState(() { _loading = true; _error = null; });
    try {
      final result = await widget.api.search(q, widget.session);
      if (!mounted) return;
      setState(() { _results = result.items; _total = result.total; _loading = false; });
    } catch (e) {
      if (!mounted) return;
      setState(() { _error = '$e'; _loading = false; });
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
              decoration: BoxDecoration(color: AppTheme.surface, border: Border(bottom: BorderSide(color: AppTheme.line))),
              child: Row(
                children: [
                  GestureDetector(
                    onTap: () => Navigator.of(context).pop(),
                    child: const Icon(Icons.arrow_back_ios_new, size: 18, color: AppTheme.ink2),
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
                        hintStyle: TextStyle(fontSize: 14, color: AppTheme.muted),
                        isDense: true,
                      ),
                    ),
                  ),
                  GestureDetector(
                    onTap: _doSearch,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
                      decoration: BoxDecoration(color: AppTheme.brand, borderRadius: BorderRadius.circular(8)),
                      child: const Text('搜索', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: Colors.white)),
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: _loading
                  ? const Center(child: CircularProgressIndicator(color: AppTheme.brand))
                  : _error != null
                      ? Center(child: Text('搜索失败：$_error', style: const TextStyle(color: AppTheme.rose)))
                      : _results.isEmpty
                          ? const Center(child: Text('输入关键词开始搜索', style: TextStyle(fontSize: 14, color: AppTheme.muted)))
                          : ListView.builder(
                              padding: const EdgeInsets.all(16),
                              itemCount: _results.length + 1,
                              itemBuilder: (ctx, i) {
                                if (i == 0) {
                                  return Padding(
                                    padding: const EdgeInsets.only(bottom: 12),
                                    child: Text('找到 $_total 条结果', style: const TextStyle(fontSize: 12.5, color: AppTheme.muted)),
                                  );
                                }
                                final item = _results[i - 1];
                                return Container(
                                  margin: const EdgeInsets.only(bottom: 12),
                                  padding: const EdgeInsets.all(14),
                                  decoration: BoxDecoration(
                                    color: AppTheme.surface,
                                    borderRadius: BorderRadius.circular(AppTheme.radiusSm),
                                    border: Border.all(color: AppTheme.line),
                                  ),
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      if (item.sourceName != null) ...[                                        Container(
                                          padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                                          decoration: BoxDecoration(color: AppTheme.brandSoft, borderRadius: BorderRadius.circular(5)),
                                          child: Text(item.sourceName!, style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w600, color: AppTheme.brandInk)),
                                        ),
                                        const SizedBox(height: 6),
                                      ],
                                      Text(item.title, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: AppTheme.ink, height: 1.4)),
                                      if (item.snippet.isNotEmpty) ...[                                        const SizedBox(height: 6),
                                        Text(item.snippet, maxLines: 3, overflow: TextOverflow.ellipsis,
                                          style: const TextStyle(fontSize: 12.5, color: AppTheme.ink2, height: 1.5)),
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

