import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'app_theme.dart';
import 'information_api.dart';

class MyActionsPage extends StatefulWidget {
  const MyActionsPage({super.key, required this.api, required this.session});
  final CampusApi api;
  final LoginSession session;

  @override
  State<MyActionsPage> createState() => _MyActionsPageState();
}

class _MyActionsPageState extends State<MyActionsPage> {
  List<ActionItem> _items = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final items = await widget.api.fetchActions(widget.session);
      if (!mounted) return;
      setState(() { _items = items; _loading = false; });
    } catch (e) {
      if (!mounted) return;
      setState(() { _error = '$e'; _loading = false; });
    }
  }

  Future<void> _openUrl(String? url) async {
    if (url == null) return;
    final uri = Uri.tryParse(url);
    if (uri != null && (uri.scheme == 'http' || uri.scheme == 'https')) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
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
                border: Border(bottom: BorderSide(color: AppTheme.line)),
              ),
              child: Row(
                children: [
                  GestureDetector(
                    onTap: () => Navigator.of(context).pop(),
                    child: const Icon(Icons.arrow_back_ios_new, size: 18, color: AppTheme.ink2),
                  ),
                  const SizedBox(width: 10),
                  const Text('我的行动', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800, color: AppTheme.ink)),
                  const Spacer(),
                  GestureDetector(
                    onTap: () { setState(() => _loading = true); _load(); },
                    child: const Icon(Icons.refresh, size: 18, color: AppTheme.muted),
                  ),
                ],
              ),
            ),
            Expanded(
              child: _loading
                  ? const Center(child: CircularProgressIndicator(color: AppTheme.brand))
                  : _error != null
                      ? Center(child: Text('加载失败：$_error', style: const TextStyle(color: AppTheme.rose)))
                      : _items.isEmpty
                          ? const _EmptyState()
                          : ListView.separated(
                              padding: const EdgeInsets.all(16),
                              itemCount: _items.length,
                              separatorBuilder: (_, __) => const SizedBox(height: 10),
                              itemBuilder: (ctx, i) => _ActionCard(
                                item: _items[i],
                                onOpenUrl: _openUrl,
                              ),
                            ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.check_circle_outline, size: 48, color: AppTheme.muted.withValues(alpha: 0.5)),
          const SizedBox(height: 12),
          const Text('暂无行动', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: AppTheme.muted)),
          const SizedBox(height: 6),
          Text('在信息详情页确认行动后，会出现在这里', style: TextStyle(fontSize: 12, color: AppTheme.muted)),
        ],
      ),
    );
  }
}

class _ActionCard extends StatelessWidget {
  const _ActionCard({required this.item, required this.onOpenUrl});
  final ActionItem item;
  final void Function(String?) onOpenUrl;

  @override
  Widget build(BuildContext context) {
    final expired = item.isExpired;
    final dueSoon = item.isDueSoon;
    final fgColor = expired ? AppTheme.muted : AppTheme.ink;
    final bgColor = expired ? AppTheme.surface2 : AppTheme.surface;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(AppTheme.radiusSm),
        border: Border.all(color: expired ? AppTheme.line : (dueSoon ? const Color(0xFFBE123C) : AppTheme.line)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Status badge + source
          Row(
            children: [
              if (dueSoon && !expired)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                  decoration: BoxDecoration(color: AppTheme.roseSoft, borderRadius: BorderRadius.circular(5)),
                  child: const Text('即将到期', style: TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: Color(0xFFBE123C))),
                ),
              if (expired)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                  decoration: BoxDecoration(color: AppTheme.surface2, borderRadius: BorderRadius.circular(5)),
                  child: const Text('已过期', style: TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: AppTheme.muted)),
                ),
              if (!expired && !dueSoon)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                  decoration: BoxDecoration(color: AppTheme.brandSoft, borderRadius: BorderRadius.circular(5)),
                  child: const Text('进行中', style: TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: AppTheme.brandInk)),
                ),
              const SizedBox(width: 8),
              if (item.sourceName != null)
                Text(item.sourceName!, style: TextStyle(fontSize: 11, color: AppTheme.muted)),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            item.title,
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: fgColor, height: 1.4),
          ),
          if (item.sourceTitle != null) ...[
            const SizedBox(height: 4),
            Text(item.sourceTitle!, maxLines: 1, overflow: TextOverflow.ellipsis,
                style: TextStyle(fontSize: 11.5, color: AppTheme.muted)),
          ],
          if (item.dueAt != null) ...[
            const SizedBox(height: 6),
            Row(
              children: [
                Icon(Icons.access_time, size: 13, color: expired ? AppTheme.muted : const Color(0xFFBE123C)),
                const SizedBox(width: 4),
                Text(
                  _formatDateTime(item.dueAt!),
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: expired ? AppTheme.muted : const Color(0xFFBE123C)),
                ),
              ],
            ),
          ],
          if (item.requiredMaterials.isNotEmpty) ...[
            const SizedBox(height: 8),
            Wrap(
              spacing: 6, runSpacing: 4,
              children: item.requiredMaterials.map((m) => Container(
                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
                decoration: BoxDecoration(color: AppTheme.surface2, borderRadius: BorderRadius.circular(5)),
                child: Text(m, style: const TextStyle(fontSize: 11, color: AppTheme.ink2)),
              )).toList(),
            ),
          ],
          if (item.originalUrl != null && item.originalUrl!.isNotEmpty) ...[
            const SizedBox(height: 8),
            GestureDetector(
              onTap: () => onOpenUrl(item.originalUrl),
              child: const Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.open_in_new, size: 13, color: AppTheme.brandInk),
                  SizedBox(width: 4),
                  Text('查看原文', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppTheme.brandInk)),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  static String _formatDateTime(DateTime dt) {
    return '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')} '
        '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }
}
