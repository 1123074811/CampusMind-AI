import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';
import 'app_theme.dart';
import 'information_api.dart';
import 'reminders_page.dart';

class PrototypeHomePage extends StatefulWidget {
  const PrototypeHomePage({
    super.key,
    required this.items,
    required this.onOpenDetail,
    required this.onOpenImport,
    required this.userName,
    required this.api,
    required this.session,
    this.onItemUpdated,
  });
  final List<InformationItem> items;
  final ValueChanged<InformationItem> onOpenDetail;
  final VoidCallback onOpenImport;
  final String userName;
  final CampusApi api;
  final LoginSession session;
  final ValueChanged<InformationItem>? onItemUpdated;

  @override
  State<PrototypeHomePage> createState() => _PrototypeHomePageState();
}

class _PrototypeHomePageState extends State<PrototypeHomePage> {
  int _activeChip = 0;
  int _reminderCount = 0;
  String _sortMode = 'smart';
  String _briefingSummary = '';

  static const _sortOptions = <String, String>{
    'smart': '智能排序',
    'newest': '最新发布',
    'hot': '热度优先',
  };

  static const _chipLabels = ['全部', '教务通知', '课程学术', '校园活动', '失物招领', '实习招聘'];
  static const _chipKeywords = <String, List<String>>{
    '教务通知': ['教务', '选课', '考试', '成绩', '教学'],
    '课程学术': ['课程', '讲座', '学术', '研讨', '学院'],
    '校园活动': ['活动', '社团', '志愿', '比赛', '校园'],
    '失物招领': ['失物', '招领', '后勤'],
    '实习招聘': ['实习', '招聘', '就业', '岗位'],
  };

  List<InformationItem> get _filteredItems {
    if (_activeChip == 0) return widget.items;
    final keywords = _chipKeywords[_chipLabels[_activeChip]] ?? const [];
    return widget.items.where((item) {
      final text = '${item.sourceName} ${item.title}';
      return keywords.any((k) => text.contains(k));
    }).toList();
  }

  @override
  void initState() {
    super.initState();
    _loadReminderCount();
    _loadBriefing();
  }

  Future<void> _loadBriefing() async {
    try {
      final briefing = await widget.api.fetchDailyBriefing(widget.session);
      if (!mounted) return;
      setState(() {
        _briefingSummary = briefing.summary;
      });
    } catch (_) {
      // 回退：从本地 items 生成
      if (!mounted) return;
      setState(() {});
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('AI 日报暂不可用，已根据当前真实信息生成概览')),
      );
    }
  }

  Future<void> _loadReminderCount() async {
    try {
      final reminders = await widget.api.fetchReminders(widget.session);
      if (!mounted) return;
      final pending =
          reminders.where((r) => !r.isDismissed && !r.isExpired).length;
      setState(() => _reminderCount = pending);
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('提醒数量暂时无法同步')),
      );
    }
  }

  Future<void> _toggleFavorite(InformationItem item) async {
    final isFav = item.readStatus == 'FAVORITED';
    final newStatus = isFav ? 'READ' : 'FAVORITED';
    try {
      final updated =
          await widget.api.updateReadStatus(item.id, newStatus, widget.session);
      if (!mounted) return;
      widget.onItemUpdated?.call(updated);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(isFav ? '已取消收藏' : '已加入收藏')),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('操作失败，请重试')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final items = _filteredItems;
    final urgentCount = items.where((e) => e.readStatus == 'NEW').length;
    // 动态计算相关度
    final relevance = items.isEmpty
        ? 0
        : ((items.where((e) => e.recommendReasons.isNotEmpty).length /
                    items.length) *
                100)
            .round();
    // 动态生成日报摘要
    final briefingText = _briefingSummary.isNotEmpty
        ? _briefingSummary
        : _generateBriefing(items);
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 6, 20, 22),
      children: [
        // Header
        _AppHeader(userName: widget.userName),
        const SizedBox(height: 18),
        // AI Hero
        _AiHeroPanel(
          total: items.length,
          urgent: urgentCount,
          relevancePercent: relevance,
          briefingSummary: briefingText,
          reminderCount: _reminderCount,
          onImport: widget.onOpenImport,
          onOpenReminders: () {
            Navigator.of(context)
                .push(
                  MaterialPageRoute(
                    builder: (_) =>
                        RemindersPage(api: widget.api, session: widget.session),
                  ),
                )
                .then((_) => _loadReminderCount());
          },
        ),
        const SizedBox(height: 18),
        // Chips
        _CategoryChips(
          labels: _chipLabels,
          selectedIndex: _activeChip,
          onChanged: (i) => setState(() => _activeChip = i),
        ),
        const SizedBox(height: 16),
        // Section title with sort dropdown
        _SectionTitle(
          title: '为你推荐',
          action: '${_sortOptions[_sortMode] ?? '智能排序'} ▾',
          onAction: () => _showSortMenu(context),
        ),
        const SizedBox(height: 12),
        // Feed cards
        if (items.isEmpty)
          const _EmptyFeed()
        else
          ...items.map(
            (item) => Padding(
              padding: const EdgeInsets.only(bottom: 14),
              child: _FeedCard(
                item: item,
                onTap: () => widget.onOpenDetail(item),
                onFavorite: () => _toggleFavorite(item),
                onShare: () => _shareItem(item),
              ),
            ),
          ),
      ],
    );
  }

  void _showSortMenu(BuildContext context) {
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
              const Text('排序方式',
                  style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                      color: AppTheme.ink)),
              const SizedBox(height: 12),
              ..._sortOptions.entries.map((entry) => ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(
                      entry.key == _sortMode
                          ? Icons.radio_button_checked
                          : Icons.radio_button_off,
                      color: entry.key == _sortMode
                          ? AppTheme.brand
                          : AppTheme.muted,
                    ),
                    title: Text(entry.value,
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                          color: entry.key == _sortMode
                              ? AppTheme.brandInk
                              : AppTheme.ink,
                        )),
                    onTap: () {
                      Navigator.of(ctx).pop();
                      setState(() => _sortMode = entry.key);
                    },
                  )),
            ],
          ),
        ),
      ),
    );
  }

  String _generateBriefing(List<InformationItem> items) {
    if (items.isEmpty) return '暂无新的校园信息。';
    final titles = items.take(3).map((e) => e.title).toList();
    if (titles.length == 1) return titles.first;
    final joined = titles.take(2).join('、');
    return titles.length > 2
        ? '$joined 等 ${items.length} 条信息值得关注。'
        : '$joined。';
  }

  void _shareItem(InformationItem item) {
    final url = item.safeOriginalUri?.toString() ?? '';
    final text = '${item.title}${url.isNotEmpty ? '\n$url' : ''}';
    SharePlus.instance.share(ShareParams(text: text, subject: item.title));
  }
}

class _AppHeader extends StatelessWidget {
  const _AppHeader({required this.userName});
  final String userName;

  @override
  Widget build(BuildContext context) {
    final hour = DateTime.now().hour;
    final greeting = hour < 12
        ? '上午好'
        : hour < 18
            ? '下午好'
            : '晚上好';
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(greeting,
                style: const TextStyle(
                    fontSize: 13,
                    color: AppTheme.muted,
                    fontWeight: FontWeight.w500)),
            const SizedBox(height: 2),
            Text(userName,
                style: const TextStyle(
                    fontSize: 21,
                    fontWeight: FontWeight.w800,
                    color: AppTheme.ink,
                    letterSpacing: -0.2)),
          ],
        ),
        Container(
          width: 42,
          height: 42,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            gradient: AppTheme.brandGradient,
            boxShadow: [
              BoxShadow(
                  color: AppTheme.brand.withValues(alpha: 0.35),
                  blurRadius: 16,
                  offset: const Offset(0, 6))
            ],
          ),
          child: Center(
            child: Text(
              userName.isNotEmpty ? userName[0] : '我',
              style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w700,
                  fontSize: 15),
            ),
          ),
        ),
      ],
    );
  }
}

class _AiHeroPanel extends StatelessWidget {
  const _AiHeroPanel({
    required this.total,
    required this.urgent,
    required this.relevancePercent,
    required this.briefingSummary,
    required this.reminderCount,
    required this.onImport,
    required this.onOpenReminders,
  });
  final int total;
  final int urgent;
  final int relevancePercent;
  final String briefingSummary;
  final int reminderCount;
  final VoidCallback onImport;
  final VoidCallback onOpenReminders;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(AppTheme.radius),
        gradient: AppTheme.brandDarkGradient,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.auto_awesome, color: Colors.white, size: 15),
              const SizedBox(width: 7),
              Text('AI 智能日报 · 已为你筛选',
                  style: TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w600,
                      color: Colors.white.withValues(alpha: 0.95))),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            '今天有 $urgent 条信息需要你优先处理',
            style: const TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w700,
                color: Colors.white,
                height: 1.35),
          ),
          const SizedBox(height: 6),
          Text(
            briefingSummary.isNotEmpty ? briefingSummary : '暂无新的校园信息。',
            style: TextStyle(
                fontSize: 13,
                color: Colors.white.withValues(alpha: 0.9),
                height: 1.55),
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              _StatItem(value: '$total', label: '今日精选'),
              const SizedBox(width: 18),
              _StatItem(value: '$urgent', label: '紧急/重要'),
              const SizedBox(width: 18),
              _StatItem(value: '$relevancePercent%', label: '相关度'),
            ],
          ),
          if (reminderCount > 0) ...[
            const SizedBox(height: 12),
            GestureDetector(
              onTap: onOpenReminders,
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: const Color(0xFFBE123C).withValues(alpha: 0.25),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.notifications_active,
                        color: Colors.white, size: 15),
                    const SizedBox(width: 6),
                    Text('待处理提醒 $reminderCount 条',
                        style: const TextStyle(
                            fontSize: 12.5,
                            fontWeight: FontWeight.w700,
                            color: Colors.white)),
                    const SizedBox(width: 4),
                    const Icon(Icons.chevron_right,
                        color: Colors.white, size: 16),
                  ],
                ),
              ),
            ),
          ],
          const SizedBox(height: 12),
          // 导入入口
          GestureDetector(
            onTap: onImport,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.18),
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.add_circle_outline, color: Colors.white, size: 16),
                  SizedBox(width: 6),
                  Text('导入信息',
                      style: TextStyle(
                          fontSize: 12.5,
                          fontWeight: FontWeight.w700,
                          color: Colors.white)),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _StatItem extends StatelessWidget {
  const _StatItem({required this.value, required this.label});
  final String value;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(value,
            style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w800,
                color: Colors.white)),
        Text(label,
            style: TextStyle(
                fontSize: 11, color: Colors.white.withValues(alpha: 0.8))),
      ],
    );
  }
}

class _CategoryChips extends StatelessWidget {
  const _CategoryChips({
    required this.labels,
    required this.selectedIndex,
    required this.onChanged,
  });
  final List<String> labels;
  final int selectedIndex;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 38,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: labels.length,
        separatorBuilder: (_, __) => const SizedBox(width: 9),
        itemBuilder: (context, i) => GestureDetector(
          onTap: () => onChanged(i),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 14),
            decoration: BoxDecoration(
              color: selectedIndex == i ? AppTheme.ink : AppTheme.surface,
              borderRadius: BorderRadius.circular(999),
              border: Border.all(
                  color: selectedIndex == i ? AppTheme.ink : AppTheme.line),
            ),
            alignment: Alignment.center,
            child: Text(
              labels[i],
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: selectedIndex == i ? Colors.white : AppTheme.ink2,
              ),
            ),
          ),
        ),
      ),
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

class _FeedCard extends StatelessWidget {
  const _FeedCard(
      {required this.item, required this.onTap, this.onFavorite, this.onShare});
  final InformationItem item;
  final VoidCallback onTap;
  final VoidCallback? onFavorite;
  final VoidCallback? onShare;

  @override
  Widget build(BuildContext context) {
    final isNew = item.readStatus == 'NEW';
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(15),
        decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(AppTheme.radiusSm),
          border: Border.all(color: AppTheme.line),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Meta row
            Row(
              children: [
                _SrcTag(text: item.sourceName, primary: isNew),
                const SizedBox(width: 8),
                _ImportanceBadge(level: isNew ? 'urgent' : 'mid'),
                const SizedBox(width: 6),
                Container(
                    width: 3,
                    height: 3,
                    decoration: const BoxDecoration(
                        color: AppTheme.muted, shape: BoxShape.circle)),
                const SizedBox(width: 6),
                Text(item.displayTime,
                    style: const TextStyle(
                        fontSize: 11.5,
                        color: AppTheme.muted,
                        fontWeight: FontWeight.w500)),
              ],
            ),
            const SizedBox(height: 9),
            Text(item.title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w700,
                    color: AppTheme.ink,
                    height: 1.4,
                    letterSpacing: -0.15)),
            if (item.preview.isNotEmpty) ...[
              const SizedBox(height: 9),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                    color: AppTheme.surface2,
                    borderRadius: BorderRadius.circular(9)),
                child: Text.rich(
                  TextSpan(
                    style: const TextStyle(
                        fontSize: 12.5, color: AppTheme.ink2, height: 1.55),
                    children: [
                      const TextSpan(
                          text: 'AI 摘要：',
                          style: TextStyle(
                              color: AppTheme.brandInk,
                              fontWeight: FontWeight.w700)),
                      TextSpan(text: item.preview),
                    ],
                  ),
                  maxLines: 3,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
            // 推荐理由标签
            if (item.recommendReasons.isNotEmpty) ...[
              const SizedBox(height: 8),
              Wrap(
                spacing: 6,
                runSpacing: 4,
                children: item.recommendReasons
                    .map((reason) => Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 7, vertical: 3),
                          decoration: BoxDecoration(
                            color: AppTheme.brandSoft,
                            borderRadius: BorderRadius.circular(5),
                          ),
                          child: Text(reason,
                              style: const TextStyle(
                                  fontSize: 11,
                                  color: AppTheme.brandInk,
                                  fontWeight: FontWeight.w500)),
                        ))
                    .toList(),
              ),
            ],
            const SizedBox(height: 10),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  item.recommendReasons.isNotEmpty
                      ? '含 ${item.recommendReasons.length + 1} 条相关来源已融合'
                      : '独立来源',
                  style: TextStyle(fontSize: 11.5, color: AppTheme.muted),
                ),
                Row(children: [
                  GestureDetector(
                    onTap: onFavorite,
                    child: Text('收藏',
                        style: TextStyle(
                            fontSize: 11.5,
                            color: item.readStatus == 'FAVORITED'
                                ? AppTheme.brand
                                : AppTheme.muted,
                            fontWeight: FontWeight.w600)),
                  ),
                  const SizedBox(width: 14),
                  GestureDetector(
                    onTap: onShare,
                    child: Text('转发',
                        style:
                            TextStyle(fontSize: 11.5, color: AppTheme.muted)),
                  ),
                ]),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _SrcTag extends StatelessWidget {
  const _SrcTag({required this.text, this.primary = false});
  final String text;
  final bool primary;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: primary ? AppTheme.brandSoft : AppTheme.surface2,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(text,
          style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              color: primary ? AppTheme.brandInk : AppTheme.ink2)),
    );
  }
}

class _ImportanceBadge extends StatelessWidget {
  const _ImportanceBadge({required this.level});
  final String level;

  @override
  Widget build(BuildContext context) {
    final Color fg;
    final Color bg;
    final String label;
    switch (level) {
      case 'urgent':
        fg = const Color(0xFFBE123C);
        bg = AppTheme.roseSoft;
        label = '紧急';
      case 'high':
        fg = const Color(0xFFB45309);
        bg = AppTheme.accentSoft;
        label = '重要';
      default:
        fg = AppTheme.brandInk;
        bg = AppTheme.brandSoft;
        label = '一般';
    }
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration:
          BoxDecoration(color: bg, borderRadius: BorderRadius.circular(6)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
              width: 6,
              height: 6,
              decoration: BoxDecoration(color: fg, shape: BoxShape.circle)),
          const SizedBox(width: 4),
          Text(label,
              style: TextStyle(
                  fontSize: 11, fontWeight: FontWeight.w700, color: fg)),
        ],
      ),
    );
  }
}

class _EmptyFeed extends StatelessWidget {
  const _EmptyFeed();
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(40),
      decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(AppTheme.radiusSm),
          border: Border.all(color: AppTheme.line)),
      child: const Column(children: [
        Icon(Icons.inbox_outlined, size: 40, color: AppTheme.muted),
        SizedBox(height: 12),
        Text('暂无新信息',
            style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: AppTheme.muted)),
      ]),
    );
  }
}
