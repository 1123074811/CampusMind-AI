import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';
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
  final Set<String> _confirmedActions = {};
  List<RelatedItem> _relatedItems = [];
  bool _relatedLoading = true;

  @override
  void initState() {
    super.initState();
    _fav = _item.readStatus == 'FAVORITED';
    _loadDetail();
    _loadRelated();
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

  Future<void> _loadRelated() async {
    try {
      final items = await widget.api.fetchRelatedItems(_item.id, widget.session);
      if (!mounted) return;
      setState(() { _relatedItems = items; _relatedLoading = false; });
    } catch (_) {
      if (!mounted) return;
      setState(() => _relatedLoading = false);
    }
  }

  Future<void> _shareItem() async {
    final url = _item.safeOriginalUri?.toString() ?? '';
    final text = '${_item.title}${url.isNotEmpty ? '\n$url' : ''}';
    await Share.share(text, subject: _item.title);
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

  Future<void> _openOriginal() async {
    final uri = _item.safeOriginalUri;
    if (uri == null || !await launchUrl(uri, mode: LaunchMode.externalApplication)) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('原文链接不可用')),
      );
    }
  }

  Future<void> _confirmAction(String title) async {
    try {
      await widget.api.confirmAction(_item.id, title, widget.session);
      if (!mounted) return;
      setState(() => _confirmedActions.add(title));
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('已加入我的行动；有明确截止时间时会创建站内提醒')),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('行动暂时无法确认，请稍后重试')),
      );
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
                    _IconBtn(icon: Icons.share_outlined, onTap: _shareItem),
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
            if (_item.hasValidAiSummary) ...[
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
                    const Row(
                      children: [
                        Icon(Icons.auto_awesome, size: 15, color: AppTheme.brandInk),
                        SizedBox(width: 7),
                        Text('AI 智能摘要', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: AppTheme.brandInk)),
                      ],
                    ),
                    const SizedBox(height: 10),
                    Text(_item.aiSummary, style: const TextStyle(fontSize: 12.5, color: AppTheme.ink2, height: 1.5)),
                  ],
                ),
              ),
              const SizedBox(height: 18),
            ],
            if (_item.confirmableActions.isNotEmpty) ...[
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AppTheme.surface,
                  borderRadius: BorderRadius.circular(AppTheme.radiusSm),
                  border: Border.all(color: AppTheme.line),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('建议行动', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: AppTheme.ink)),
                    const SizedBox(height: 8),
                    for (final action in _item.confirmableActions)
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        dense: true,
                        title: Text(action),
                        trailing: _confirmedActions.contains(action)
                            ? const Icon(Icons.check_circle, color: AppTheme.brand)
                            : TextButton(onPressed: () => _confirmAction(action), child: const Text('确认加入')),
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 18),
            ],
            // Body
            Text(
              _item.detailContent.isNotEmpty ? _item.detailContent : _item.preview,
              style: const TextStyle(fontSize: 13, color: AppTheme.ink2, height: 1.7),
            ),
            const SizedBox(height: 20),
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: AppTheme.surface,
                borderRadius: BorderRadius.circular(AppTheme.radiusSm),
                border: Border.all(color: AppTheme.line),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('来源与校验信息', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: AppTheme.ink)),
                  const SizedBox(height: 10),
                  Text('来源：${_item.sourceName}', style: const TextStyle(fontSize: 12, color: AppTheme.ink2)),
                  const SizedBox(height: 5),
                  Text('抓取时间：${_item.fetchedDisplayTime}', style: const TextStyle(fontSize: 12, color: AppTheme.ink2)),
                  if (_item.contentHash.isNotEmpty) ...[
                    const SizedBox(height: 5),
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: SelectableText('内容哈希：${_item.contentHash}', style: const TextStyle(fontSize: 11, color: AppTheme.muted)),
                        ),
                        IconButton(
                          visualDensity: VisualDensity.compact,
                          tooltip: '复制内容哈希',
                          onPressed: () => Clipboard.setData(ClipboardData(text: _item.contentHash)),
                          icon: const Icon(Icons.copy, size: 15),
                        ),
                      ],
                    ),
                  ],
                  if (_item.safeOriginalUri != null) ...[
                    const SizedBox(height: 8),
                    TextButton.icon(
                      onPressed: _openOriginal,
                      icon: const Icon(Icons.open_in_new, size: 16),
                      label: const Text('查看原文'),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 22),
            // Related
            if (_relatedItems.isNotEmpty || _relatedLoading) ...[              const Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('相关信息 · 去重融合', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: AppTheme.ink)),
                ],
              ),
              const SizedBox(height: 12),
              if (_relatedLoading)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 16),
                  child: Center(child: SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))),
                )
              else
                ..._relatedItems.asMap().entries.map((entry) {
                  final related = entry.value;
                  return Padding(
                    padding: EdgeInsets.only(bottom: entry.key < _relatedItems.length - 1 ? 10 : 0),
                    child: GestureDetector(
                      onTap: () {
                        Navigator.of(context).push(MaterialPageRoute(
                          builder: (_) => PrototypeDetailPage(
                            item: InformationItem(
                              id: related.id,
                              title: related.title,
                              sourceName: related.sourceName,
                              preview: '',
                              originalUrl: '',
                              readStatus: 'NEW',
                              itemStatus: 'ACTIVE',
                              fetchedAt: DateTime.now(),
                            ),
                            api: widget.api,
                            session: widget.session,
                            onItemChanged: (_) {},
                          ),
                        ));
                      },
                      child: _RelatedCard(
                        source: related.sourceName,
                        time: related.displayTime,
                        title: related.title,
                        fuse: related.fuseNote,
                      ),
                    ),
                  );
                }),
            ],
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
