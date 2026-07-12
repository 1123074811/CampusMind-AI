import 'package:flutter/material.dart';
import 'app_theme.dart';
import 'information_api.dart';

class MySubscriptionsPage extends StatefulWidget {
  const MySubscriptionsPage({super.key, required this.api, required this.session});
  final CampusApi api;
  final LoginSession session;

  @override
  State<MySubscriptionsPage> createState() => _MySubscriptionsPageState();
}

class _MySubscriptionsPageState extends State<MySubscriptionsPage> {
  List<SubscriptionItem> _items = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final items = await widget.api.fetchSubscriptions(widget.session);
      if (!mounted) return;
      setState(() { _items = items; _loading = false; });
    } catch (_) {
      if (!mounted) return;
      setState(() => _loading = false);
    }
  }

  Future<void> _toggle(SubscriptionItem item) async {
    final newEnabled = !item.enabled;
    try {
      await widget.api.updateSubscription(item.sourceId, newEnabled, widget.session);
      if (!mounted) return;
      setState(() {
        final idx = _items.indexWhere((e) => e.sourceId == item.sourceId);
        if (idx != -1) {
          _items[idx] = SubscriptionItem(
            sourceId: item.sourceId,
            sourceName: item.sourceName,
            sourceType: item.sourceType,
            enabled: newEnabled,
            subscribedAt: item.subscribedAt,
          );
        }
      });
    } catch (_) {}
  }

  String _sourceTypeLabel(String type) {
    switch (type) {
      case 'PUBLIC_WEB': return '自动抓取';
      case 'RAIN_CLASSROOM': return '雨课堂';
      case 'USER_TEXT': return '手动提交';
      case 'USER_IMAGE': return '截图识别';
      case 'USER_FILE': return '文件上传';
      default: return type;
    }
  }

  IconData _sourceTypeIcon(String type) {
    switch (type) {
      case 'PUBLIC_WEB': return Icons.language;
      case 'RAIN_CLASSROOM': return Icons.school_outlined;
      case 'USER_TEXT': return Icons.article_outlined;
      case 'USER_IMAGE': return Icons.image_outlined;
      case 'USER_FILE': return Icons.attach_file;
      default: return Icons.rss_feed;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      body: SafeArea(
        child: Column(
          children: [
            // Header
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 6, 20, 14),
              child: Row(
                children: [
                  GestureDetector(
                    onTap: () => Navigator.of(context).pop(),
                    child: Container(
                      width: 36, height: 36,
                      decoration: BoxDecoration(color: AppTheme.surface, borderRadius: BorderRadius.circular(11), border: Border.all(color: AppTheme.line)),
                      child: const Icon(Icons.arrow_back_ios_new, size: 17, color: AppTheme.ink2),
                    ),
                  ),
                  const SizedBox(width: 12),
                  const Text('我的订阅', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: AppTheme.ink)),
                ],
              ),
            ),
            Expanded(
              child: _loading
                  ? const Center(child: CircularProgressIndicator())
                  : _items.isEmpty
                      ? _emptyState()
                      : ListView.separated(
                          padding: const EdgeInsets.symmetric(horizontal: 20),
                          itemCount: _items.length,
                          separatorBuilder: (_, __) => const SizedBox(height: 10),
                          itemBuilder: (context, i) {
                            final item = _items[i];
                            return Container(
                              padding: const EdgeInsets.all(14),
                              decoration: BoxDecoration(
                                color: AppTheme.surface,
                                borderRadius: BorderRadius.circular(AppTheme.radiusSm),
                                border: Border.all(color: AppTheme.line),
                              ),
                              child: Row(
                                children: [
                                  Container(
                                    width: 42, height: 42,
                                    decoration: BoxDecoration(
                                      color: item.enabled ? AppTheme.brandSoft : AppTheme.surface2,
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                    child: Icon(
                                      _sourceTypeIcon(item.sourceType),
                                      size: 20,
                                      color: item.enabled ? AppTheme.brandInk : AppTheme.muted,
                                    ),
                                  ),
                                  const SizedBox(width: 12),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(item.sourceName, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: AppTheme.ink)),
                                        const SizedBox(height: 3),
                                        Text(
                                          '${_sourceTypeLabel(item.sourceType)} · ${item.enabled ? "已启用" : "已暂停"}',
                                          style: TextStyle(fontSize: 12, color: item.enabled ? AppTheme.muted : AppTheme.rose),
                                        ),
                                      ],
                                    ),
                                  ),
                                  GestureDetector(
                                    onTap: () => _toggle(item),
                                    child: AnimatedContainer(
                                      duration: const Duration(milliseconds: 200),
                                      width: 44, height: 24,
                                      decoration: BoxDecoration(
                                        color: item.enabled ? AppTheme.brand : AppTheme.surface2,
                                        borderRadius: BorderRadius.circular(12),
                                      ),
                                      child: Padding(
                                        padding: const EdgeInsets.all(2),
                                        child: AnimatedAlign(
                                          duration: const Duration(milliseconds: 200),
                                          alignment: item.enabled ? Alignment.centerRight : Alignment.centerLeft,
                                          child: Container(
                                            width: 20, height: 20,
                                            decoration: const BoxDecoration(
                                              color: Colors.white,
                                              shape: BoxShape.circle,
                                            ),
                                          ),
                                        ),
                                      ),
                                    ),
                                  ),
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

  Widget _emptyState() {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.rss_feed, size: 48, color: AppTheme.muted.withValues(alpha: 0.5)),
          const SizedBox(height: 12),
          const Text('暂无订阅', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppTheme.muted)),
          const SizedBox(height: 6),
          const Text('系统已自动为你订阅所有启用的数据源', style: TextStyle(fontSize: 12, color: AppTheme.muted)),
        ],
      ),
    );
  }
}
