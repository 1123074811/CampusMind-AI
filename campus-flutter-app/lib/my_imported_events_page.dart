import 'package:flutter/material.dart';

import 'app_theme.dart';
import 'information_api.dart';

class MyImportedEventsPage extends StatefulWidget {
  const MyImportedEventsPage({super.key, required this.api, required this.session});
  final CampusApi api;
  final LoginSession session;

  @override
  State<MyImportedEventsPage> createState() => _MyImportedEventsPageState();
}

class _MyImportedEventsPageState extends State<MyImportedEventsPage> {
  var _loading = true;
  List<ImportedEventItem> _items = const [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final items = await widget.api.fetchRainEvents(widget.session);
      if (mounted) setState(() => _items = items);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AppTheme.bg,
        appBar: AppBar(title: const Text('我的雨课堂导入'), centerTitle: true),
        body: _loading
            ? const Center(child: CircularProgressIndicator())
            : _items.isEmpty
                ? const Center(child: Text('暂无已导入课程、作业或通知'))
                : ListView.separated(
                    padding: const EdgeInsets.all(16),
                    itemCount: _items.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 10),
                    itemBuilder: (_, index) {
                      final item = _items[index];
                      return Container(
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          color: AppTheme.surface,
                          borderRadius: BorderRadius.circular(AppTheme.radiusSm),
                          border: Border.all(color: AppTheme.line),
                        ),
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Text(item.title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: AppTheme.ink)),
                          if (item.summary.isNotEmpty) ...[
                            const SizedBox(height: 6),
                            Text(item.summary, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(color: AppTheme.ink2)),
                          ],
                          const SizedBox(height: 10),
                          Text('雨课堂 · ${_typeLabel(item.eventType)} · 仅自己可见', style: const TextStyle(fontSize: 12, color: AppTheme.muted)),
                        ]),
                      );
                    },
                  ),
      );

  String _typeLabel(String value) => switch (value) {
        'HOMEWORK' => '作业',
        'COURSE' => '课程',
        'NOTICE' => '通知',
        'EXAM' => '考试',
        _ => '其他',
      };
}
