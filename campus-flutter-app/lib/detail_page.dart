import 'package:flutter/material.dart';
import 'app_theme.dart';
import 'information_api.dart';

class PrototypeDetailPage extends StatefulWidget {
  const PrototypeDetailPage({
    super.key,
    required this.item,
    required this.api,
    required this.session,
    required this.onItemChanged,
  });
  final InformationItem item;
  final CampusApi api;
  final LoginSession session;
  final ValueChanged<InformationItem> onItemChanged;

  @override
  State<PrototypeDetailPage> createState() => _PrototypeDetailPageState();
}

class _PrototypeDetailPageState extends State<PrototypeDetailPage> {
  late InformationItem _item = widget.item;
  bool _fav = false;

  @override
  void initState() {
    super.initState();
    _fav = _item.readStatus == 'FAVORITED';
    _loadDetail();
  }

  Future<void> _loadDetail() async {
    try {
      var item = await widget.api.fetchInformationDetail(_item.id, widget.session);
      if (item.readStatus == 'NEW') {
        try { item = await widget.api.updateReadStatus(item.id, 'READ', widget.session); } catch (_) {}
      }
      if (!mounted) return;
      setState(() => _item = item);
      widget.onItemChanged(item);
    } catch (_) {}
  }

  Future<void> _toggleFav() async {
    final newStatus = _fav ? 'READ' : 'FAVORITED';
    try {
      final item = await widget.api.updateReadStatus(_item.id, newStatus, widget.session);
      if (!mounted) return;
      setState(() { _item = item; _fav = !_fav; });
      widget.onItemChanged(item);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_fav ? '已加入收藏' : '已取消收藏')),
      );
    } catch (_) {
      setState(() => _fav = !_fav);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 6, 20, 28),
          children: [
            // Header
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                _IconBtn(icon: Icons.arrow_back_ios_new, onTap: () => Navigator.of(context).pop()),
                Row(
                  children: [
                    _IconBtn(icon: _fav ? Icons.bookmark : Icons.bookmark_outline, onTap: _toggleFav, active: _fav),
                    const SizedBox(width: 8),
                    _IconBtn(icon: Icons.share_outlined, onTap: () {
                      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('分享链接已复制')));
                    }),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 16),
            // Source row
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(color: AppTheme.brandSoft, borderRadius: BorderRadius.circular(6)),
                  child: Text(_item.sourceName, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: AppTheme.brandInk)),
                ),
                const SizedBox(width: 8),
                Text(_item.displayTime, style: const TextStyle(fontSize: 12, color: AppTheme.muted, fontWeight: FontWeight.w500)),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(color: AppTheme.roseSoft, borderRadius: BorderRadius.circular(6)),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Container(width: 6, height: 6, decoration: const BoxDecoration(color: Color(0xFFBE123C), shape: BoxShape.circle)),
                      const SizedBox(width: 4),
                      const Text('紧急', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: Color(0xFFBE123C))),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            // Title
            Text(_item.title, style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w800, color: AppTheme.ink, height: 1.35, letterSpacing: -0.2)),
            const SizedBox(height: 16),
            // AI Summary
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: const Color(0xFFE2F6EE),
                borderRadius: BorderRadius.circular(AppTheme.radiusSm),
                border: Border.all(color: const Color(0xFFCDEEDE)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.auto_awesome, size: 15, color: AppTheme.brandInk),
                      const SizedBox(width: 7),
                      const Text('AI 智能摘要', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: AppTheme.brandInk)),
                    ],
                  ),
                  const SizedBox(height: 10),
                  ...[
                    '今晚 22:00–24:00 选课系统暂停服务，请提前保存志愿。',
                    '维护期间提交的选课请求将不生效，需次日重试。',
                    '升级后新增「课程冲突检测」实时提示功能。',
                  ].map((text) => Padding(
                    padding: const EdgeInsets.only(bottom: 7),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(width: 6, height: 6, margin: const EdgeInsets.only(top: 6), decoration: const BoxDecoration(color: AppTheme.brand, shape: BoxShape.circle)),
                        const SizedBox(width: 8),
                        Expanded(child: Text(text, style: const TextStyle(fontSize: 12.5, color: AppTheme.ink2, height: 1.5))),
                      ],
                    ),
                  )),
                ],
              ),
            ),
            const SizedBox(height: 18),
            // Body
            Text(
              _item.detailContent.isNotEmpty ? _item.detailContent : _item.preview,
              style: const TextStyle(fontSize: 13, color: AppTheme.ink2, height: 1.7),
            ),
            const SizedBox(height: 22),
            // Related
            const Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('相关信息 · 去重融合', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: AppTheme.ink)),
              ],
            ),
            const SizedBox(height: 12),
            _RelatedCard(source: '辅导员通知', time: '10 分钟前', title: '班群提醒：今晚选课系统维护，志愿请提前存', fuse: '与「教务处」来源已融合为 1 条'),
            const SizedBox(height: 10),
            _RelatedCard(source: '官网公告', time: '25 分钟前', title: '选课系统 v3.2 升级说明（冲突检测上线）', fuse: '补充背景，已关联上文'),
          ],
        ),
      ),
    );
  }
}

class _IconBtn extends StatelessWidget {
  const _IconBtn({required this.icon, required this.onTap, this.active = false});
  final IconData icon;
  final VoidCallback onTap;
  final bool active;
  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 36, height: 36,
        decoration: BoxDecoration(
          color: active ? AppTheme.brandSoft : AppTheme.surface,
          borderRadius: BorderRadius.circular(11),
          border: Border.all(color: active ? AppTheme.brand : AppTheme.line),
        ),
        child: Icon(icon, size: 17, color: active ? AppTheme.brand : AppTheme.ink2),
      ),
    );
  }
}

class _RelatedCard extends StatelessWidget {
  const _RelatedCard({required this.source, required this.time, required this.title, required this.fuse});
  final String source;
  final String time;
  final String title;
  final String fuse;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: AppTheme.surface, borderRadius: BorderRadius.circular(AppTheme.radiusSm), border: Border.all(color: AppTheme.line)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(color: AppTheme.surface2, borderRadius: BorderRadius.circular(6)),
                child: Text(source, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: AppTheme.ink2)),
              ),
              const SizedBox(width: 7),
              Text(time, style: const TextStyle(fontSize: 11, color: AppTheme.muted)),
            ],
          ),
          const SizedBox(height: 6),
          Text(title, style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700, color: AppTheme.ink, height: 1.4)),
          const SizedBox(height: 6),
          Row(
            children: [
              const Icon(Icons.close, size: 13, color: AppTheme.brandInk),
              const SizedBox(width: 5),
              Text(fuse, style: const TextStyle(fontSize: 11, color: AppTheme.brandInk, fontWeight: FontWeight.w600)),
            ],
          ),
        ],
      ),
    );
  }
}
