import 'package:flutter/material.dart';
import 'app_theme.dart';
import 'information_api.dart';

class DataSourcePreferencePage extends StatefulWidget {
  const DataSourcePreferencePage(
      {super.key, required this.api, required this.session});
  final CampusApi api;
  final LoginSession session;

  @override
  State<DataSourcePreferencePage> createState() =>
      _DataSourcePreferencePageState();
}

class _DataSourcePreferencePageState extends State<DataSourcePreferencePage> {
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
      setState(() {
        _items = items;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _loading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('数据源偏好加载失败，请重试')),
      );
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
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('操作失败，请重试')),
      );
    }
  }

  String _typeLabel(String type) {
    switch (type) {
      case 'PUBLIC_WEB':
        return '公开网页';
      case 'RAIN_CLASSROOM':
        return '雨课堂';
      default:
        return type;
    }
  }

  IconData _typeIcon(String type) {
    switch (type) {
      case 'PUBLIC_WEB':
        return Icons.language;
      case 'RAIN_CLASSROOM':
        return Icons.school_outlined;
      default:
        return Icons.rss_feed;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      appBar: AppBar(
        backgroundColor: AppTheme.bg,
        elevation: 0,
        title: const Text('数据源偏好',
            style: TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w700,
                color: AppTheme.ink)),
        iconTheme: const IconThemeData(color: AppTheme.ink),
      ),
      body: _loading
          ? const Center(
              child: SizedBox(
                  width: 24,
                  height: 24,
                  child: CircularProgressIndicator(strokeWidth: 2)))
          : _items.isEmpty
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.inbox_outlined,
                          size: 48, color: AppTheme.muted),
                      const SizedBox(height: 12),
                      const Text('暂无订阅数据源',
                          style:
                              TextStyle(fontSize: 14, color: AppTheme.muted)),
                      const SizedBox(height: 8),
                      TextButton(
                        onPressed: () => Navigator.of(context).pop(),
                        child: const Text('去发现页订阅'),
                      ),
                    ],
                  ),
                )
              : ListView(
                  padding: const EdgeInsets.fromLTRB(20, 10, 20, 24),
                  children: [
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppTheme.brandSoft,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.info_outline,
                              color: AppTheme.brandInk, size: 18),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              '开启的数据源将参与信息采集和 AI 分析，关闭后不再抓取新内容。',
                              style: const TextStyle(
                                  fontSize: 12,
                                  color: AppTheme.brandInk,
                                  height: 1.5),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                    Text('已订阅数据源 (${_items.length})',
                        style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                            color: AppTheme.ink2)),
                    const SizedBox(height: 10),
                    Container(
                      decoration: BoxDecoration(
                        color: AppTheme.surface,
                        borderRadius: BorderRadius.circular(AppTheme.radiusSm),
                        border: Border.all(color: AppTheme.line),
                      ),
                      child: Column(
                        children: List.generate(_items.length, (i) {
                          final item = _items[i];
                          return Column(
                            children: [
                              Padding(
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 15, vertical: 13),
                                child: Row(
                                  children: [
                                    Container(
                                      width: 36,
                                      height: 36,
                                      decoration: BoxDecoration(
                                        color: item.enabled
                                            ? AppTheme.brandSoft
                                            : AppTheme.surface2,
                                        borderRadius: BorderRadius.circular(10),
                                      ),
                                      child: Icon(_typeIcon(item.sourceType),
                                          color: item.enabled
                                              ? AppTheme.brandInk
                                              : AppTheme.muted,
                                          size: 18),
                                    ),
                                    const SizedBox(width: 12),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        children: [
                                          Text(item.sourceName,
                                              style: const TextStyle(
                                                  fontSize: 14,
                                                  fontWeight: FontWeight.w600,
                                                  color: AppTheme.ink)),
                                          Text(_typeLabel(item.sourceType),
                                              style: const TextStyle(
                                                  fontSize: 11,
                                                  color: AppTheme.muted)),
                                        ],
                                      ),
                                    ),
                                    GestureDetector(
                                      onTap: () => _toggle(i),
                                      child: AnimatedContainer(
                                        duration:
                                            const Duration(milliseconds: 200),
                                        width: 42,
                                        height: 24,
                                        padding: const EdgeInsets.all(3),
                                        decoration: BoxDecoration(
                                          color: item.enabled
                                              ? AppTheme.brand
                                              : AppTheme.surface2,
                                          borderRadius:
                                              BorderRadius.circular(99),
                                        ),
                                        child: AnimatedAlign(
                                          duration:
                                              const Duration(milliseconds: 200),
                                          alignment: item.enabled
                                              ? Alignment.centerRight
                                              : Alignment.centerLeft,
                                          child: Container(
                                              width: 18,
                                              height: 18,
                                              decoration: const BoxDecoration(
                                                  color: Colors.white,
                                                  shape: BoxShape.circle)),
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
                    ),
                  ],
                ),
    );
  }
}
