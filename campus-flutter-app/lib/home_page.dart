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
    required this.total,
    this.onItemUpdated,
    this.onLoadMore,
    this.hasMore = false,
    this.loadingMore = false,
    this.feedMode = 'ALL',
    required this.onFeedModeChanged,
  });
  final List<InformationItem> items;
  final ValueChanged<InformationItem> onOpenDetail;
  final VoidCallback onOpenImport;
  final String userName;
  final CampusApi api;
  final LoginSession session;
  final int total;
  final ValueChanged<InformationItem>? onItemUpdated;
  final Future<void> Function()? onLoadMore;
  final bool hasMore;
  final bool loadingMore;
  final String feedMode;
  final ValueChanged<String> onFeedModeChanged;

  @override
  State<PrototypeHomePage> createState() => _PrototypeHomePageState();
}

class _PrototypeHomePageState extends State<PrototypeHomePage> {
  int _activeChip = 0;
  int _reminderCount = 0;
  int _unreadDeliveryCount = 0;
  List<NotificationDelivery> _recentDeliveries = const [];
  String _sortMode = 'smart';
  String _briefingSummary = '';
  final _bellKey = GlobalKey();
  OverlayEntry? _bellOverlay;

  static const _sortOptions = <String, String>{
    'smart': '智能排序',
    'newest': '最新发布',
    'hot': '热度优先',
  };

  /// 过滤芯片：前几项为业务类型，后三项为快捷视图。
  static const _chipDefs = <(String, String)>[
    ('全部', 'ALL'),
    ('通知', 'NOTICE'),
    ('课程', 'COURSE'),
    ('考试', 'EXAM'),
    ('作业', 'HOMEWORK'),
    ('活动', 'ACTIVITY'),
    ('讲座', 'LECTURE'),
    ('竞赛', 'COMPETITION'),
    ('服务', 'SERVICE'),
    ('仅未读', 'UNREAD'),
    ('即将截止', 'DUE_SOON'),
    ('与我相关', 'RELEVANT'),
  ];

  bool _isDueSoon(InformationItem item) =>
      item.importanceLevelAt(DateTime.now()) == 'urgent';

  List<InformationItem> get _filteredItems {
    final key = _chipDefs[_activeChip].$2;
    final List<InformationItem> filtered;
    switch (key) {
      case 'ALL':
        filtered = List<InformationItem>.from(widget.items);
        break;
      case 'UNREAD':
        filtered =
            widget.items.where((item) => item.readStatus == 'NEW').toList();
        break;
      case 'DUE_SOON':
        filtered = widget.items.where(_isDueSoon).toList();
        break;
      case 'RELEVANT':
        filtered = widget.items
            .where((item) =>
                item.recommendReasons.isNotEmpty ||
                item.readStatus == 'FAVORITED')
            .toList();
        break;
      default:
        filtered = widget.items
            .where((item) => item.eventType.toUpperCase() == key)
            .toList();
    }
    return _sortedItems(filtered);
  }

  List<(String, int)> get _chipStats {
    return List.generate(_chipDefs.length, (index) {
      final key = _chipDefs[index].$2;
      final count = switch (key) {
        'ALL' => widget.total,
        'UNREAD' =>
          widget.items.where((item) => item.readStatus == 'NEW').length,
        'DUE_SOON' => widget.items.where(_isDueSoon).length,
        'RELEVANT' => widget.items
            .where((item) =>
                item.recommendReasons.isNotEmpty ||
                item.readStatus == 'FAVORITED')
            .length,
        _ => widget.items
            .where((item) => item.eventType.toUpperCase() == key)
            .length,
      };
      return (_chipDefs[index].$1, count);
    });
  }

  List<InformationItem> _sortedItems(List<InformationItem> items) {
    final sorted = List<InformationItem>.from(items);
    switch (_sortMode) {
      case 'newest':
        // 最新发布：严格按发布时间倒序；无发布时间时退回抓取时间
        sorted.sort((a, b) {
          final byRelease = _timestamp(b).compareTo(_timestamp(a));
          if (byRelease != 0) return byRelease;
          final byFetched =
              b.fetchedAt.millisecondsSinceEpoch
                  .compareTo(a.fetchedAt.millisecondsSinceEpoch);
          if (byFetched != 0) return byFetched;
          return b.id.compareTo(a.id);
        });
        break;
      case 'hot':
        sorted.sort((a, b) {
          final byScore = _hotScore(b).compareTo(_hotScore(a));
          if (byScore != 0) return byScore;
          final byRelease = _timestamp(b).compareTo(_timestamp(a));
          if (byRelease != 0) return byRelease;
          return b.id.compareTo(a.id);
        });
        break;
      case 'smart':
      default:
        // 保留服务端推荐顺序（订阅优先 + 抓取时间）
        break;
    }
    return sorted;
  }

  /// 与卡片展示时间一致：优先 publishTime，否则 fetchedAt。
  int _timestamp(InformationItem item) =>
      (item.publishTime ?? item.fetchedAt).millisecondsSinceEpoch;

  int _hotScore(InformationItem item) {
    var score = 0;
    if (item.readStatus == 'NEW') score += 3;
    if (item.readStatus == 'FAVORITED') score += 2;
    score += item.recommendReasons.length;
    if (item.hasValidAiSummary) score += 1;
    return score;
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
      final results = await Future.wait([
        widget.api.fetchReminders(widget.session),
        widget.api.fetchNotificationDeliveries(widget.session),
      ]);
      if (!mounted) return;
      final reminders = results[0] as List<ReminderItem>;
      final deliveries = results[1] as List<NotificationDelivery>;
      final pending =
          reminders.where((r) => !r.isDismissed && !r.isExpired).length;
      final unread = deliveries.where((d) => d.isUnreadInApp).length;
      final recent = List<NotificationDelivery>.from(deliveries)
        ..sort((a, b) => b.id.compareTo(a.id));
      setState(() {
        _reminderCount = pending;
        _unreadDeliveryCount = unread;
        _recentDeliveries = recent.take(8).toList();
      });
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('提醒数量暂时无法同步')),
      );
    }
  }

  void _openInbox() {
    final renderBox = _bellKey.currentContext?.findRenderObject() as RenderBox?;
    if (renderBox == null) return;
    final bellPos = renderBox.localToGlobal(Offset.zero);
    final bellSize = renderBox.size;
    final screenWidth = MediaQuery.of(context).size.width;

    _bellOverlay = OverlayEntry(
      builder: (ctx) => Stack(
        children: [
          // tap outside to dismiss
          Positioned.fill(
            child: GestureDetector(
              onTap: _closeBellPopup,
              behavior: HitTestBehavior.translucent,
              child: Container(color: Colors.black.withValues(alpha: 0.18)),
            ),
          ),
          // popup card
          Positioned(
            right: 20,
            top: bellPos.dy + bellSize.height + 6,
            width: screenWidth - 40,
            child: Material(
              elevation: 12,
              borderRadius: BorderRadius.circular(16),
              color: AppTheme.surface,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Text('站内通知',
                            style: TextStyle(
                                fontSize: 15,
                                fontWeight: FontWeight.w800,
                                color: AppTheme.ink)),
                        const Spacer(),
                        TextButton(
                          onPressed: () {
                            _closeBellPopup();
                            Navigator.of(context)
                                .push(
                                  MaterialPageRoute(
                                    builder: (_) => RemindersPage(
                                        api: widget.api,
                                        session: widget.session),
                                  ),
                                )
                                .then((_) => _loadReminderCount());
                          },
                          style: TextButton.styleFrom(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 10, vertical: 4),
                              minimumSize: Size.zero),
                          child: const Text('查看全部',
                              style: TextStyle(fontSize: 12)),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    if (_recentDeliveries.isEmpty)
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 18),
                        child: Center(
                          child: Text('暂无站内投递记录',
                              style:
                                  TextStyle(color: AppTheme.muted, fontSize: 13)),
                        ),
                      )
                    else
                      ConstrainedBox(
                        constraints: BoxConstraints(
                          maxHeight: MediaQuery.of(ctx).size.height * 0.42,
                        ),
                        child: SingleChildScrollView(
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: _recentDeliveries
                                .map((delivery) => _BellDeliveryTile(
                                      delivery: delivery,
                                      onTap: () {
                                        _closeBellPopup();
                                        Navigator.of(context)
                                            .push(
                                              MaterialPageRoute(
                                                builder: (_) => RemindersPage(
                                                    api: widget.api,
                                                    session: widget.session,
                                                    initialReminderId:
                                                        delivery.reminderId),
                                              ),
                                            )
                                            .then(
                                                (_) => _loadReminderCount());
                                      },
                                    ))
                                .toList(),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
    Overlay.of(context).insert(_bellOverlay!);
  }

  void _closeBellPopup() {
    _bellOverlay?.remove();
    _bellOverlay = null;
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
    final now = DateTime.now();
    final urgentCount =
        items.where((item) => item.importanceLevelAt(now) != null).length;
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
    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification.metrics.extentAfter < 300 &&
            widget.hasMore &&
            !widget.loadingMore) {
          widget.onLoadMore?.call();
        }
        return false;
      },
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 6, 20, 22),
        children: [
        // Header
        _AppHeader(
          bellKey: _bellKey,
          userName: widget.userName,
          inboxCount: _unreadDeliveryCount,
          onOpenInbox: _openInbox,
        ),
        const SizedBox(height: 18),
        // AI Hero
        _AiHeroPanel(
          total: _activeChip == 0 ? widget.total : items.length,
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
        SegmentedButton<String>(
          segments: const [
            ButtonSegment(value: 'ALL', label: Text('全部')),
            ButtonSegment(value: 'SUBSCRIBED_ONLY', label: Text('仅订阅')),
          ],
          selected: {widget.feedMode},
          showSelectedIcon: false,
          onSelectionChanged: (value) => widget.onFeedModeChanged(value.first),
        ),
        const SizedBox(height: 12),
        // Chips with real counts
        _CategoryChips(
          chips: _chipStats,
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
          _EmptyFeed(
            title: _chipDefs[_activeChip].$2 == 'ALL' ? '暂无信息' : '当前筛选下暂无内容',
            subtitle: _chipDefs[_activeChip].$2 == 'ALL'
                ? '去订阅数据源或导入校园通知后会显示在这里'
                : '试试切换类型，或查看“全部”',
          )
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
        if (widget.loadingMore)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 12),
            child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
          ),
        ],
      ),
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
  const _AppHeader({
    required this.bellKey,
    required this.userName,
    required this.inboxCount,
    required this.onOpenInbox,
  });
  final GlobalKey bellKey;
  final String userName;
  final int inboxCount;
  final VoidCallback onOpenInbox;

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
        Row(
          children: [
            GestureDetector(
              onTap: onOpenInbox,
              child: Stack(
                clipBehavior: Clip.none,
                children: [
                  Container(
                    key: bellKey,
                    width: 42,
                    height: 42,
                    decoration: BoxDecoration(
                      color: AppTheme.surface,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: AppTheme.line),
                    ),
                    child: const Icon(Icons.notifications_none,
                        color: AppTheme.ink2, size: 20),
                  ),
                  if (inboxCount > 0)
                    Positioned(
                      right: -2,
                      top: -2,
                      child: Container(
                        constraints: const BoxConstraints(minWidth: 16),
                        height: 16,
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        decoration: BoxDecoration(
                          color: AppTheme.rose,
                          borderRadius: BorderRadius.circular(999),
                        ),
                        alignment: Alignment.center,
                        child: Text(
                          inboxCount > 99 ? '99+' : '$inboxCount',
                          style: const TextStyle(
                              color: Colors.white,
                              fontSize: 10,
                              fontWeight: FontWeight.w800),
                        ),
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(width: 10),
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
    required this.chips,
    required this.selectedIndex,
    required this.onChanged,
  });
  final List<(String, int)> chips;
  final int selectedIndex;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 38,
      child: ListView.separated(
        primary: false,
        scrollDirection: Axis.horizontal,
        physics: const BouncingScrollPhysics(
          parent: AlwaysScrollableScrollPhysics(),
        ),
        itemCount: chips.length,
        separatorBuilder: (_, __) => const SizedBox(width: 9),
        itemBuilder: (context, i) {
          final label = chips[i].$1;
          final count = chips[i].$2;
          final active = selectedIndex == i;
          return Material(
            color: Colors.transparent,
            child: InkWell(
              borderRadius: BorderRadius.circular(999),
              onTap: () => onChanged(i),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12),
                decoration: BoxDecoration(
                  color: active ? AppTheme.ink : AppTheme.surface,
                  borderRadius: BorderRadius.circular(999),
                  border:
                      Border.all(color: active ? AppTheme.ink : AppTheme.line),
                ),
                alignment: Alignment.center,
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      label,
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: active ? Colors.white : AppTheme.ink2,
                      ),
                    ),
                    const SizedBox(width: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 6, vertical: 1),
                      decoration: BoxDecoration(
                        color: active
                            ? Colors.white.withValues(alpha: 0.18)
                            : AppTheme.surface2,
                        borderRadius: BorderRadius.circular(999),
                      ),
                      child: Text(
                        '$count',
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          color: active ? Colors.white : AppTheme.muted,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        },
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
    final importance = item.importanceLevelAt(DateTime.now());
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
                if (importance != null) ...[
                  const SizedBox(width: 8),
                  _ImportanceBadge(level: importance),
                ],
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
  const _EmptyFeed({
    this.title = '暂无新信息',
    this.subtitle = '订阅数据源或导入校园通知后会显示在这里',
  });
  final String title;
  final String subtitle;
  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(36),
      decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(AppTheme.radiusSm),
          border: Border.all(color: AppTheme.line)),
      child: Column(children: [
        const Icon(Icons.inbox_outlined, size: 40, color: AppTheme.muted),
        const SizedBox(height: 12),
        Text(title,
            style: const TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w700,
                color: AppTheme.ink2)),
        const SizedBox(height: 6),
        Text(subtitle,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 12.5, color: AppTheme.muted)),
      ]),
    );
  }
}

class _BellDeliveryTile extends StatelessWidget {
  const _BellDeliveryTile({required this.delivery, required this.onTap});
  final NotificationDelivery delivery;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final isFailed = delivery.status == 'FAILED';
    final isWithdrawn = delivery.status == 'WITHDRAWN';
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 5),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 30,
              height: 30,
              decoration: BoxDecoration(
                color: isFailed
                    ? AppTheme.roseSoft
                    : isWithdrawn
                        ? AppTheme.surface2
                        : AppTheme.brandSoft,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(
                isFailed
                    ? Icons.error_outline
                    : isWithdrawn
                        ? Icons.undo
                        : Icons.notifications_active_outlined,
                color: isFailed
                    ? AppTheme.rose
                    : isWithdrawn
                        ? AppTheme.muted
                        : AppTheme.brandInk,
                size: 15,
              ),
            ),
            const SizedBox(width: 9),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '提醒 #${delivery.reminderId} · ${delivery.statusLabel}',
                    style: const TextStyle(
                        fontSize: 12.5,
                        fontWeight: FontWeight.w700,
                        color: AppTheme.ink),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    [
                      delivery.channel,
                      if (delivery.sentAt != null)
                        delivery.sentAt!.toLocal().toString().substring(0, 16),
                      if (delivery.lastError != null &&
                          delivery.lastError!.isNotEmpty)
                        delivery.lastError!,
                    ].join(' · '),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        fontSize: 11.5, color: AppTheme.muted),
                  ),
                ],
              ),
            ),
            const Icon(Icons.chevron_right,
                color: AppTheme.muted, size: 16),
          ],
        ),
      ),
    );
  }
}
