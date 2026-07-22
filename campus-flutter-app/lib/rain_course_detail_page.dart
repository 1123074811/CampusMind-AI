import 'package:flutter/material.dart';

import 'app_theme.dart';
import 'information_api.dart';

class RainCourseDetailPage extends StatefulWidget {
  const RainCourseDetailPage({
    super.key,
    required this.course,
    required this.api,
    required this.session,
  });

  final InformationItem course;
  final CampusApi api;
  final LoginSession session;

  @override
  State<RainCourseDetailPage> createState() => _RainCourseDetailPageState();
}

class _RainCourseDetailPageState extends State<RainCourseDetailPage> {
  static const _filters = <String, String>{
    'ALL': '全部',
    'NOTICE': '通知',
    'HOMEWORK': '作业',
    'EXAM': '考试',
  };

  var _loading = true;
  String? _error;
  var _filter = 'ALL';
  ImportedEventItem? _courseEvent;
  List<ImportedEventItem> _messages = const [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final events = await widget.api.fetchRainEvents(widget.session);
      final courseName = _normalize(widget.course.title);
      final courses = events.where((item) =>
          item.eventType == 'COURSE' && _normalize(item.title) == courseName);
      final messages = events
          .where((item) =>
              item.eventType != 'COURSE' &&
              _normalize(item.courseName) == courseName)
          .toList()
        ..sort((a, b) => _newestFirst(a.time, b.time));
      if (!mounted) return;
      setState(() {
        _courseEvent = courses.firstOrNull;
        _messages = messages;
      });
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _normalize(String value) =>
      value.replaceAll(RegExp(r'\s+'), '').trim().toLowerCase();

  int _newestFirst(DateTime? left, DateTime? right) {
    if (left == null) return right == null ? 0 : 1;
    if (right == null) return -1;
    return right.compareTo(left);
  }

  List<ImportedEventItem> get _visibleMessages => _filter == 'ALL'
      ? _messages
      : _messages.where((item) => item.eventType == _filter).toList();

  int _count(String type) => type == 'ALL'
      ? _messages.length
      : _messages.where((item) => item.eventType == type).length;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      appBar: AppBar(
        backgroundColor: AppTheme.bg,
        surfaceTintColor: Colors.transparent,
        title: const Text('课程详情'),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(strokeWidth: 2.5))
          : _error != null
              ? _ErrorState(message: _error!, onRetry: _load)
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
                    children: [
                      _CourseHeader(
                        title: widget.course.title,
                        semester: _courseEvent?.semester,
                        organizer: _courseEvent?.organizer,
                        messageCount: _messages.length,
                      ),
                      const SizedBox(height: 22),
                      const Text(
                        '课程动态',
                        style: TextStyle(
                          color: AppTheme.ink,
                          fontSize: 20,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 12),
                      SingleChildScrollView(
                        scrollDirection: Axis.horizontal,
                        child: Row(
                          children: _filters.entries.map((entry) {
                            final selected = _filter == entry.key;
                            return Padding(
                              padding: const EdgeInsets.only(right: 8),
                              child: ChoiceChip(
                                selected: selected,
                                onSelected: (_) =>
                                    setState(() => _filter = entry.key),
                                label:
                                    Text('${entry.value} ${_count(entry.key)}'),
                                selectedColor: AppTheme.brandSoft,
                                side: BorderSide(
                                  color:
                                      selected ? AppTheme.brand : AppTheme.line,
                                ),
                                labelStyle: TextStyle(
                                  color: selected
                                      ? AppTheme.brandInk
                                      : AppTheme.ink2,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            );
                          }).toList(),
                        ),
                      ),
                      const SizedBox(height: 16),
                      if (_visibleMessages.isEmpty)
                        const _EmptyMessages()
                      else
                        ..._visibleMessages.map(
                          (item) => Padding(
                            padding: const EdgeInsets.only(bottom: 12),
                            child: _MessageCard(
                              item: item,
                              onTap: () => _showMessage(item),
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
    );
  }

  void _showMessage(ImportedEventItem item) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppTheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (context) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.72,
        maxChildSize: 0.92,
        builder: (context, controller) => ListView(
          controller: controller,
          padding: const EdgeInsets.fromLTRB(24, 12, 24, 32),
          children: [
            Center(
              child: Container(
                width: 42,
                height: 4,
                decoration: BoxDecoration(
                  color: AppTheme.line,
                  borderRadius: BorderRadius.circular(99),
                ),
              ),
            ),
            const SizedBox(height: 22),
            Text(
              _typeLabel(item.eventType),
              style: const TextStyle(
                color: AppTheme.brandInk,
                fontSize: 13,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              item.title,
              style: const TextStyle(
                color: AppTheme.ink,
                fontSize: 22,
                fontWeight: FontWeight.w800,
                height: 1.35,
              ),
            ),
            const SizedBox(height: 14),
            _MetaLine(
              icon: Icons.schedule_outlined,
              text: item.time == null
                  ? '发布时间未知'
                  : '发布于 ${_formatTime(item.time!)}',
            ),
            if (item.deadline != null) ...[
              const SizedBox(height: 8),
              _MetaLine(
                icon: Icons.event_outlined,
                text: '截止 ${_formatTime(item.deadline!)}',
              ),
            ],
            if (item.summary.trim().isNotEmpty) ...[
              const SizedBox(height: 24),
              Text(
                item.summary.trim(),
                style: const TextStyle(
                  color: AppTheme.ink2,
                  fontSize: 16,
                  height: 1.75,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _CourseHeader extends StatelessWidget {
  const _CourseHeader({
    required this.title,
    required this.semester,
    required this.organizer,
    required this.messageCount,
  });

  final String title;
  final String? semester;
  final String? organizer;
  final int messageCount;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: AppTheme.brandInk,
        borderRadius: BorderRadius.circular(AppTheme.radius),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(Icons.school_outlined, color: Colors.white70, size: 18),
              SizedBox(width: 7),
              Text(
                '雨课堂课程',
                style: TextStyle(
                  color: Colors.white70,
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Text(
            title,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 24,
              fontWeight: FontWeight.w800,
              height: 1.35,
            ),
          ),
          const SizedBox(height: 18),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              if (semester != null && semester != '未标注学期')
                _HeaderPill(text: semester!),
              if (organizer != null && organizer!.trim().isNotEmpty)
                _HeaderPill(text: organizer!),
              _HeaderPill(text: '$messageCount 条动态'),
            ],
          ),
        ],
      ),
    );
  }
}

class _HeaderPill extends StatelessWidget {
  const _HeaderPill({required this.text});
  final String text;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 7),
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(99),
        ),
        child: Text(
          text,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 12,
            fontWeight: FontWeight.w700,
          ),
        ),
      );
}

class _MessageCard extends StatelessWidget {
  const _MessageCard({required this.item, required this.onTap});
  final ImportedEventItem item;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final color = _typeColor(item.eventType);
    return Material(
      color: AppTheme.surface,
      borderRadius: BorderRadius.circular(AppTheme.radiusSm),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppTheme.radiusSm),
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            border: Border.all(color: AppTheme.line),
            borderRadius: BorderRadius.circular(AppTheme.radiusSm),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(11),
                ),
                child: Icon(_typeIcon(item.eventType), color: color, size: 20),
              ),
              const SizedBox(width: 13),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(
                          _typeLabel(item.eventType),
                          style: TextStyle(
                            color: color,
                            fontSize: 12,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const Spacer(),
                        Text(
                          item.time == null
                              ? '发布时间未知'
                              : _formatTime(item.time!),
                          style: const TextStyle(
                            color: AppTheme.muted,
                            fontSize: 12,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 7),
                    Text(
                      item.title,
                      style: const TextStyle(
                        color: AppTheme.ink,
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        height: 1.4,
                      ),
                    ),
                    if (item.deadline != null) ...[
                      const SizedBox(height: 8),
                      Text(
                        '截止 ${_formatTime(item.deadline!)}',
                        style: const TextStyle(
                          color: AppTheme.rose,
                          fontSize: 12.5,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(width: 4),
              const Icon(Icons.chevron_right, color: AppTheme.muted, size: 20),
            ],
          ),
        ),
      ),
    );
  }
}

class _MetaLine extends StatelessWidget {
  const _MetaLine({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Row(
        children: [
          Icon(icon, color: AppTheme.muted, size: 17),
          const SizedBox(width: 8),
          Text(text,
              style: const TextStyle(color: AppTheme.muted, fontSize: 13)),
        ],
      );
}

class _EmptyMessages extends StatelessWidget {
  const _EmptyMessages();

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 44),
        decoration: BoxDecoration(
          color: AppTheme.surface,
          border: Border.all(color: AppTheme.line),
          borderRadius: BorderRadius.circular(AppTheme.radius),
        ),
        child: const Column(
          children: [
            Icon(Icons.inbox_outlined, color: AppTheme.muted, size: 36),
            SizedBox(height: 12),
            Text('暂无该类型的课程动态',
                style: TextStyle(
                    color: AppTheme.ink2, fontWeight: FontWeight.w700)),
          ],
        ),
      );
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.cloud_off_outlined,
                  color: AppTheme.muted, size: 40),
              const SizedBox(height: 12),
              Text('课程动态加载失败\n$message',
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: AppTheme.muted, height: 1.5)),
              const SizedBox(height: 16),
              FilledButton(onPressed: onRetry, child: const Text('重试')),
            ],
          ),
        ),
      );
}

String _typeLabel(String type) => switch (type) {
      'HOMEWORK' => '作业',
      'EXAM' => '考试',
      'NOTICE' => '通知',
      _ => '课程消息',
    };

IconData _typeIcon(String type) => switch (type) {
      'HOMEWORK' => Icons.assignment_outlined,
      'EXAM' => Icons.fact_check_outlined,
      'NOTICE' => Icons.campaign_outlined,
      _ => Icons.notes_outlined,
    };

Color _typeColor(String type) => switch (type) {
      'HOMEWORK' => AppTheme.accent,
      'EXAM' => AppTheme.rose,
      'NOTICE' => AppTheme.info,
      _ => AppTheme.brand,
    };

String _formatTime(DateTime value) =>
    '${value.year.toString().padLeft(4, '0')}-'
    '${value.month.toString().padLeft(2, '0')}-'
    '${value.day.toString().padLeft(2, '0')} '
    '${value.hour.toString().padLeft(2, '0')}:'
    '${value.minute.toString().padLeft(2, '0')}';
