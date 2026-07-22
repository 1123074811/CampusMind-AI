import 'package:flutter/material.dart';

import 'app_theme.dart';
import 'information_api.dart';
import 'rain_sync_manager.dart';
import 'rain_sync_progress_page.dart';
import 'rain_webview_page.dart';

class MyImportedEventsPage extends StatefulWidget {
  const MyImportedEventsPage(
      {super.key, required this.api, required this.session});
  final CampusApi api;
  final LoginSession session;

  @override
  State<MyImportedEventsPage> createState() => _MyImportedEventsPageState();
}

class _MyImportedEventsPageState extends State<MyImportedEventsPage> {
  var _loading = true;
  var _refreshAfterSync = false;
  List<ImportedEventItem> _items = const [];

  @override
  void initState() {
    super.initState();
    RainSyncManager.instance.addListener(_syncChanged);
    _load();
  }

  @override
  void dispose() {
    RainSyncManager.instance.removeListener(_syncChanged);
    super.dispose();
  }

  void _syncChanged() {
    final manager = RainSyncManager.instance;
    if (_refreshAfterSync && !manager.active) {
      _refreshAfterSync = false;
      if (manager.phase == RainSyncPhase.success && mounted) {
        setState(() => _loading = true);
        _load();
      }
    }
  }

  Future<void> _load() async {
    try {
      final items = await widget.api.fetchRainEvents(widget.session);
      if (mounted) setState(() => _items = items);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openRainWebView() async {
    final manager = RainSyncManager.instance;
    if (manager.active) {
      await Navigator.of(context).push<void>(
        MaterialPageRoute(builder: (_) => const RainSyncProgressPage()),
      );
      return;
    }
    final started = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) =>
            RainWebViewPage(api: widget.api, session: widget.session),
      ),
    );
    if (!mounted || started != true) return;
    setState(() => _refreshAfterSync = true);
    await Navigator.of(context).push<void>(
      MaterialPageRoute(builder: (_) => const RainSyncProgressPage()),
    );
  }

  Widget _syncStatus() {
    final manager = RainSyncManager.instance;
    if (manager.phase == RainSyncPhase.idle) return const SizedBox.shrink();
    return AnimatedBuilder(
      animation: manager,
      builder: (context, _) => ListTile(
        onTap: () => Navigator.of(context).push<void>(
          MaterialPageRoute(builder: (_) => const RainSyncProgressPage()),
        ),
        leading: manager.active
            ? const SizedBox(
                width: 22,
                height: 22,
                child: CircularProgressIndicator(strokeWidth: 2.5),
              )
            : const Icon(Icons.check_circle, color: AppTheme.brandInk),
        title: Text(manager.active ? '雨课堂正在后台同步' : '最近一次雨课堂同步已完成'),
        subtitle: Text(
            '采集 ${manager.collected} · 写入 ${manager.success} · 重复 ${manager.skipped} · 失败 ${manager.failed}'),
        trailing: const Icon(Icons.chevron_right),
      ),
    );
  }

  void _showDetails(ImportedEventItem item) {
    showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(item.title),
        content: SingleChildScrollView(
          child: Text([
            '类型：${_typeLabel(item.eventType)}',
            if (item.summary.isNotEmpty) '内容：${item.summary}',
            '可见范围：仅自己可见',
          ].join('\n\n')),
        ),
        actions: [
          TextButton(
            onPressed: () async {
              Navigator.of(dialogContext).pop();
              await _delete(item);
            },
            child: const Text('删除', style: TextStyle(color: AppTheme.rose)),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('关闭'),
          ),
        ],
      ),
    );
  }

  Future<void> _delete(ImportedEventItem item) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('删除雨课堂记录'),
        content: Text('确定删除“${item.title}”吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('删除'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    try {
      await widget.api.deleteRainEvent(item.id, widget.session);
      if (!mounted) return;
      setState(
          () => _items = _items.where((value) => value.id != item.id).toList());
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('已删除')));
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('删除失败：$error')));
      }
    }
  }

  Future<void> _deleteAll() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('清空雨课堂导入'),
        content: const Text('将删除当前账号导入的全部雨课堂课程与消息。清空后可重新同步干净数据。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('确认清空'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    try {
      final result = await widget.api.deleteRainEvents(widget.session);
      if (!mounted) return;
      setState(() => _items = const []);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('已清空 ${result['deletedEvents'] ?? 0} 条雨课堂记录')));
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('清空失败：$error')));
      }
    }
  }

  List<_RainSemesterGroup> get _semesters {
    final contextBySource = <String, ({String course, String semester})>{};
    final semestersByCourse = <String, Set<String>>{};
    for (final item in _items) {
      if (item.courseName == '未归类消息') continue;
      if (item.sourceUrl != null) {
        final previous = contextBySource[item.sourceUrl!];
        if (previous == null ||
            (previous.semester == '未标注学期' &&
                item.semester != '未标注学期')) {
          contextBySource[item.sourceUrl!] =
              (course: item.courseName, semester: item.semester);
        }
      }
      if (item.semester != '未标注学期') {
        semestersByCourse
            .putIfAbsent(item.courseName, () => <String>{})
            .add(item.semester);
      }
    }

    final semesters = <String, _RainSemesterGroup>{};
    for (final item in _items) {
      final sourceContext = contextBySource[item.sourceUrl];
      final sourceSemester = sourceContext?.semester;
      final courseName = item.courseName == '未归类消息'
          ? sourceContext?.course ?? item.courseName
          : item.courseName;
      final knownSemesters = semestersByCourse[courseName];
      final semesterName = item.semester != '未标注学期'
          ? item.semester
          : sourceSemester != null && sourceSemester != '未标注学期'
              ? sourceSemester
              : knownSemesters?.length == 1
                  ? knownSemesters!.first
                  : item.semester;
      final semester = semesters.putIfAbsent(
          semesterName, () => _RainSemesterGroup(semesterName));
      final group = semester.courses.putIfAbsent(
          courseName, () => _RainCourseGroup(courseName));
      if (item.eventType == 'COURSE') {
        group.course ??= item;
      } else {
        group.messages.add(item);
      }
    }
    for (final semester in semesters.values) {
      for (final course in semester.courses.values) {
        course.messages.sort((a, b) => _compareNewestFirst(a.time, b.time));
      }
    }
    final result = semesters.values.toList()
      ..sort((a, b) => _semesterRank(b.name).compareTo(_semesterRank(a.name)));
    return result;
  }

  int _compareNewestFirst(DateTime? left, DateTime? right) {
    if (left == null) return right == null ? 0 : 1;
    if (right == null) return -1;
    return right.compareTo(left);
  }

  int _semesterRank(String value) {
    final match = RegExp(r'(20\d{2})([春秋])').firstMatch(value);
    if (match == null) return -1;
    return int.parse(match.group(1)!) * 2 + (match.group(2) == '秋' ? 1 : 0);
  }

  Widget _courseCard(_RainCourseGroup group) => Container(
        decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(AppTheme.radiusSm),
          border: Border.all(color: AppTheme.line),
        ),
        child: ExpansionTile(
          shape: const Border(),
          collapsedShape: const Border(),
          title: Text(group.name,
              style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: AppTheme.ink)),
          subtitle: Text('${group.messages.length} 条消息',
              style: const TextStyle(color: AppTheme.muted)),
          children: [
            if (group.course != null)
              ListTile(
                title: const Text('课程信息'),
                onTap: () => _showDetails(group.course!),
                trailing: IconButton(
                  tooltip: '删除课程记录',
                  onPressed: () => _delete(group.course!),
                  icon: const Icon(Icons.delete_outline, color: AppTheme.rose),
                ),
              ),
            ...group.messages.map(
              (message) => ListTile(
                leading: Text(_typeLabel(message.eventType),
                    style: const TextStyle(
                        color: AppTheme.brandInk, fontWeight: FontWeight.w600)),
                title: Text(message.title),
                subtitle: message.summary.isEmpty
                    ? null
                    : Text(message.summary,
                        maxLines: 2, overflow: TextOverflow.ellipsis),
                onTap: () => _showDetails(message),
                trailing: IconButton(
                  tooltip: '删除消息',
                  onPressed: () => _delete(message),
                  icon: const Icon(Icons.delete_outline, color: AppTheme.rose),
                ),
              ),
            ),
          ],
        ),
      );

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AppTheme.bg,
        appBar: AppBar(
          title: const Text('我的雨课堂导入'),
          centerTitle: true,
          actions: [
            IconButton(
              tooltip: '清空雨课堂导入',
              onPressed: _items.isEmpty ? null : _deleteAll,
              icon: const Icon(Icons.delete_sweep_outlined),
            ),
          ],
        ),
        body: Column(
          children: [
            _syncStatus(),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
              child: SizedBox(
                width: double.infinity,
                height: 48,
                child: FilledButton.icon(
                  onPressed: _openRainWebView,
                  icon: const Icon(Icons.login),
                  label: const Text('打开内置雨课堂'),
                ),
              ),
            ),
            Expanded(
              child: _loading
                  ? const Center(child: CircularProgressIndicator())
                  : _items.isEmpty
                      ? const Center(child: Text('暂无已导入课程、作业或通知'))
                      : ListView(
                          padding: const EdgeInsets.all(16),
                          children: [
                            for (final semester in _semesters) ...[
                              Padding(
                                padding: const EdgeInsets.fromLTRB(2, 8, 2, 10),
                                child: Text(semester.name,
                                    style: const TextStyle(
                                        fontSize: 20,
                                        fontWeight: FontWeight.w800,
                                        color: AppTheme.ink)),
                              ),
                              for (final course in semester.courses.values) ...[
                                _courseCard(course),
                                const SizedBox(height: 10),
                              ],
                            ],
                          ],
                        ),
            ),
          ],
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

class _RainCourseGroup {
  _RainCourseGroup(this.name);

  final String name;
  ImportedEventItem? course;
  final messages = <ImportedEventItem>[];
}

class _RainSemesterGroup {
  _RainSemesterGroup(this.name);

  final String name;
  final courses = <String, _RainCourseGroup>{};
}
