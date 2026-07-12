import 'package:flutter/material.dart';
import 'app_theme.dart';
import 'information_api.dart';

class PrototypeHomePage extends StatelessWidget {
  const PrototypeHomePage({
    super.key,
    required this.items,
    required this.onOpenDetail,
    required this.onOpenImport,
    required this.userName,
  });
  final List<InformationItem> items;
  final ValueChanged<InformationItem> onOpenDetail;
  final VoidCallback onOpenImport;
  final String userName;

  @override
  Widget build(BuildContext context) {
    final urgentCount = items.where((e) => e.readStatus == 'NEW').length;
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 6, 20, 22),
      children: [
        // Header
        _AppHeader(userName: userName),
        const SizedBox(height: 18),
        // AI Hero
        _AiHeroPanel(total: items.length, urgent: urgentCount, onImport: onOpenImport),
        const SizedBox(height: 18),
        // Chips
        const _CategoryChips(),
        const SizedBox(height: 16),
        // Section title
        const _SectionTitle(title: '为你推荐', action: '智能排序 ▾'),
        const SizedBox(height: 12),
        // Feed cards
        if (items.isEmpty)
          const _EmptyFeed()
        else
          ...items.map(
            (item) => Padding(
              padding: const EdgeInsets.only(bottom: 14),
              child: _FeedCard(item: item, onTap: () => onOpenDetail(item)),
            ),
          ),
      ],
    );
  }
}

class _AppHeader extends StatelessWidget {
  const _AppHeader({required this.userName});
  final String userName;

  @override
  Widget build(BuildContext context) {
    final hour = DateTime.now().hour;
    final greeting = hour < 12 ? '上午好' : hour < 18 ? '下午好' : '晚上好';
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(greeting, style: const TextStyle(fontSize: 13, color: AppTheme.muted, fontWeight: FontWeight.w500)),
            const SizedBox(height: 2),
            Text(userName, style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w800, color: AppTheme.ink, letterSpacing: -0.2)),
          ],
        ),
        Container(
          width: 42,
          height: 42,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            gradient: AppTheme.brandGradient,
            boxShadow: [BoxShadow(color: AppTheme.brand.withValues(alpha: 0.35), blurRadius: 16, offset: const Offset(0, 6))],
          ),
          child: Center(
            child: Text(
              userName.isNotEmpty ? userName[0] : '我',
              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 15),
            ),
          ),
        ),
      ],
    );
  }
}

class _AiHeroPanel extends StatelessWidget {
  const _AiHeroPanel({required this.total, required this.urgent, required this.onImport});
  final int total;
  final int urgent;
  final VoidCallback onImport;

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
              Text('AI 智能日报 · 已为你筛选', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: Colors.white.withValues(alpha: 0.95))),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            '今天有 $urgent 条信息需要你优先处理',
            style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: Colors.white, height: 1.35),
          ),
          const SizedBox(height: 6),
          Text(
            '选课系统今晚维护、计算机学院讲座明日截止报名、图书馆自习室已延长开放。',
            style: TextStyle(fontSize: 13, color: Colors.white.withValues(alpha: 0.9), height: 1.55),
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              _StatItem(value: '$total', label: '今日精选'),
              const SizedBox(width: 18),
              _StatItem(value: '$urgent', label: '紧急/重要'),
              const SizedBox(width: 18),
              const _StatItem(value: '86%', label: '相关度'),
            ],
          ),
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
        Text(value, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: Colors.white)),
        Text(label, style: TextStyle(fontSize: 11, color: Colors.white.withValues(alpha: 0.8))),
      ],
    );
  }
}

class _CategoryChips extends StatefulWidget {
  const _CategoryChips();

  @override
  State<_CategoryChips> createState() => _CategoryChipsState();
}

class _CategoryChipsState extends State<_CategoryChips> {
  int _active = 0;
  final _labels = const ['全部', '教务通知', '课程学术', '校园活动', '失物招领', '实习招聘'];

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 38,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: _labels.length,
        separatorBuilder: (_, __) => const SizedBox(width: 9),
        itemBuilder: (context, i) => GestureDetector(
          onTap: () => setState(() => _active = i),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 14),
            decoration: BoxDecoration(
              color: _active == i ? AppTheme.ink : AppTheme.surface,
              borderRadius: BorderRadius.circular(999),
              border: Border.all(color: _active == i ? AppTheme.ink : AppTheme.line),
            ),
            alignment: Alignment.center,
            child: Text(
              _labels[i],
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: _active == i ? Colors.white : AppTheme.ink2,
              ),
            ),
          ),
        ),
      ),
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
        if (action != null)
          Text(action!, style: const TextStyle(fontSize: 12.5, color: AppTheme.brandInk, fontWeight: FontWeight.w600)),
      ],
    );
  }
}

class _FeedCard extends StatelessWidget {
  const _FeedCard({required this.item, required this.onTap});
  final InformationItem item;
  final VoidCallback onTap;

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
                Container(width: 3, height: 3, decoration: const BoxDecoration(color: AppTheme.muted, shape: BoxShape.circle)),
                const SizedBox(width: 6),
                Text(item.displayTime, style: const TextStyle(fontSize: 11.5, color: AppTheme.muted, fontWeight: FontWeight.w500)),
              ],
            ),
            const SizedBox(height: 9),
            Text(item.title, maxLines: 2, overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: AppTheme.ink, height: 1.4, letterSpacing: -0.15)),
            if (item.preview.isNotEmpty) ...[
              const SizedBox(height: 9),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(color: AppTheme.surface2, borderRadius: BorderRadius.circular(9)),
                child: Text.rich(
                  TextSpan(
                    style: const TextStyle(fontSize: 12.5, color: AppTheme.ink2, height: 1.55),
                    children: [
                      const TextSpan(text: 'AI 摘要：', style: TextStyle(color: AppTheme.brandInk, fontWeight: FontWeight.w700)),
                      TextSpan(text: item.preview),
                    ],
                  ),
                  maxLines: 3,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
            const SizedBox(height: 10),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('含 2 条相关来源已融合', style: TextStyle(fontSize: 11.5, color: AppTheme.muted)),
                Row(children: [
                  Text('收藏', style: TextStyle(fontSize: 11.5, color: AppTheme.muted)),
                  const SizedBox(width: 14),
                  Text('转发', style: TextStyle(fontSize: 11.5, color: AppTheme.muted)),
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
      child: Text(text, style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: primary ? AppTheme.brandInk : AppTheme.ink2)),
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
        fg = const Color(0xFFBE123C); bg = AppTheme.roseSoft; label = '紧急';
      case 'high':
        fg = const Color(0xFFB45309); bg = AppTheme.accentSoft; label = '重要';
      default:
        fg = AppTheme.brandInk; bg = AppTheme.brandSoft; label = '一般';
    }
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(6)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(width: 6, height: 6, decoration: BoxDecoration(color: fg, shape: BoxShape.circle)),
          const SizedBox(width: 4),
          Text(label, style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: fg)),
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
      decoration: BoxDecoration(color: AppTheme.surface, borderRadius: BorderRadius.circular(AppTheme.radiusSm), border: Border.all(color: AppTheme.line)),
      child: const Column(children: [
        Icon(Icons.inbox_outlined, size: 40, color: AppTheme.muted),
        SizedBox(height: 12),
        Text('暂无新信息', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppTheme.muted)),
      ]),
    );
  }
}
