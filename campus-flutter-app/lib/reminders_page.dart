import 'package:flutter/material.dart';
import 'app_theme.dart';
import 'information_api.dart';

class RemindersPage extends StatefulWidget {
  const RemindersPage({super.key, required this.api, required this.session});
  final CampusApi api;
  final LoginSession session;

  @override
  State<RemindersPage> createState() => _RemindersPageState();
}

class _RemindersPageState extends State<RemindersPage> {
  List<ReminderItem> _items = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final items = await widget.api.fetchReminders(widget.session);
      if (!mounted) return;
      setState(() { _items = items; _loading = false; });
    } catch (e) {
      if (!mounted) return;
      setState(() { _error = '$e'; _loading = false; });
    }
  }

  Future<void> _dismiss(ReminderItem item) async {
    try {
      await widget.api.dismissReminder(item.id, widget.session);
      if (!mounted) return;
      setState(() {
        final idx = _items.indexWhere((r) => r.id == item.id);
        if (idx >= 0) {
          _items[idx] = ReminderItem(
            id: item.id, actionItemId: item.actionItemId,
            informationItemId: item.informationItemId,
            actionTitle: item.actionTitle, sourceTitle: item.sourceTitle,
            originalUrl: item.originalUrl, remindAt: item.remindAt,
            dueAt: item.dueAt, status: 'DISMISSED', sentAt: item.sentAt,
          );
        }
      });
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('操作失败，请重试')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      body: SafeArea(
        child: Column(
          children: [
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
                  const Text('消息提醒', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800, color: AppTheme.ink)),
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
                              itemBuilder: (ctx, i) => _ReminderCard(
                                item: _items[i],
                                onDismiss: () => _dismiss(_items[i]),
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
          Icon(Icons.notifications_none, size: 48, color: AppTheme.muted.withValues(alpha: 0.5)),
          const SizedBox(height: 12),
          const Text('暂无提醒', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: AppTheme.muted)),
          const SizedBox(height: 6),
          Text('确认行动后，系统会在截止前自动提醒你', style: TextStyle(fontSize: 12, color: AppTheme.muted)),
        ],
      ),
    );
  }
}

class _ReminderCard extends StatelessWidget {
  const _ReminderCard({required this.item, required this.onDismiss});
  final ReminderItem item;
  final VoidCallback onDismiss;

  @override
  Widget build(BuildContext context) {
    final dismissed = item.isDismissed;
    final due = item.isDue;
    final expired = item.isExpired;

    return Dismissible(
      key: ValueKey(item.id),
      direction: dismissed ? DismissDirection.none : DismissDirection.endToStart,
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 16),
        decoration: BoxDecoration(
          color: AppTheme.roseSoft,
          borderRadius: BorderRadius.circular(AppTheme.radiusSm),
        ),
        child: const Text('已消除', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: Color(0xFFBE123C))),
      ),
      onDismissed: (_) => onDismiss(),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: dismissed ? AppTheme.surface2 : AppTheme.surface,
          borderRadius: BorderRadius.circular(AppTheme.radiusSm),
          border: Border.all(color: due && !dismissed ? const Color(0xFFBE123C) : AppTheme.line),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                _StatusBadge(dismissed: dismissed, due: due, expired: expired),
                const SizedBox(width: 8),
                if (item.sourceTitle != null)
                  Expanded(
                    child: Text(item.sourceTitle!, maxLines: 1, overflow: TextOverflow.ellipsis,
                        style: TextStyle(fontSize: 11.5, color: dismissed ? AppTheme.muted : AppTheme.ink2)),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              item.actionTitle,
              style: TextStyle(
                fontSize: 14, fontWeight: FontWeight.w700,
                color: dismissed ? AppTheme.muted : AppTheme.ink, height: 1.4,
              ),
            ),
            if (item.remindAt != null) ...[
              const SizedBox(height: 6),
              Row(
                children: [
                  Icon(Icons.notifications_active, size: 13, color: dismissed ? AppTheme.muted : AppTheme.brandInk),
                  const SizedBox(width: 4),
                  Text('提醒时间：${_formatDateTime(item.remindAt!)}',
                      style: TextStyle(fontSize: 12, color: dismissed ? AppTheme.muted : AppTheme.ink2)),
                ],
              ),
            ],
            if (item.dueAt != null) ...[
              const SizedBox(height: 3),
              Row(
                children: [
                  Icon(Icons.access_time, size: 13, color: dismissed ? AppTheme.muted : const Color(0xFFBE123C)),
                  const SizedBox(width: 4),
                  Text('截止：${_formatDateTime(item.dueAt!)}',
                      style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600,
                          color: dismissed ? AppTheme.muted : const Color(0xFFBE123C))),
                ],
              ),
            ],
            if (!dismissed) ...[
              const SizedBox(height: 10),
              GestureDetector(
                onTap: onDismiss,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                  decoration: BoxDecoration(
                    border: Border.all(color: AppTheme.line),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Text('忽略此提醒', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppTheme.ink2)),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  static String _formatDateTime(DateTime dt) {
    return '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')} '
        '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.dismissed, required this.due, required this.expired});
  final bool dismissed;
  final bool due;
  final bool expired;

  @override
  Widget build(BuildContext context) {
    if (dismissed) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
        decoration: BoxDecoration(color: AppTheme.surface2, borderRadius: BorderRadius.circular(5)),
        child: const Text('已消除', style: TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: AppTheme.muted)),
      );
    }
    if (due || expired) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
        decoration: BoxDecoration(color: AppTheme.roseSoft, borderRadius: BorderRadius.circular(5)),
        child: Text(expired ? '已过期' : '待处理', style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: Color(0xFFBE123C))),
      );
    }
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
      decoration: BoxDecoration(color: AppTheme.brandSoft, borderRadius: BorderRadius.circular(5)),
      child: const Text('等待中', style: TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: AppTheme.brandInk)),
    );
  }
}
