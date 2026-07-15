import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';
import 'app_theme.dart';
import 'information_api.dart';
import 'detail_page.dart';

class PrototypeDiscoverPage extends StatelessWidget {
  const PrototypeDiscoverPage(
      {super.key, this.onOpenImport, required this.api, required this.session});
  final VoidCallback? onOpenImport;
  final CampusApi api;
  final LoginSession session;

  void _showManageChannels(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('管理信息频道',
                  style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                      color: AppTheme.ink)),
              const SizedBox(height: 12),
              const Text('在「我的订阅」中可添加/移除频道、调整订阅优先级。',
                  style: TextStyle(
                      fontSize: 13, color: AppTheme.ink2, height: 1.5)),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: TextButton(
                  onPressed: () => Navigator.of(ctx).pop(),
                  child: const Text('知道了'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 6, 20, 22),
      children: [
        const Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('发现',
                style: TextStyle(
                    fontSize: 13,
                    color: AppTheme.muted,
                    fontWeight: FontWeight.w500)),
            SizedBox(height: 2),
            Text('探索校园',
                style: TextStyle(
                    fontSize: 21,
                    fontWeight: FontWeight.w800,
                    color: AppTheme.ink)),
          ],
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(
              child: GestureDetector(
                onTap: () {
                  Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => _SearchPage(api: api, session: session),
                    ),
                  );
                },
                child: Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                  decoration: BoxDecoration(
                      color: AppTheme.surface,
                      borderRadius: BorderRadius.circular(13),
                      border: Border.all(color: AppTheme.line)),
                  child: const Row(
                    children: [
                      Icon(Icons.search, color: AppTheme.muted, size: 19),
                      SizedBox(width: 9),
                      Text('搜索通知、讲座、活动…',
                          style:
                              TextStyle(fontSize: 13.5, color: AppTheme.muted)),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(width: 10),
            GestureDetector(
              onTap: onOpenImport,
              child: Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: AppTheme.brand,
                  borderRadius: BorderRadius.circular(13),
                  boxShadow: [
                    BoxShadow(
                        color: AppTheme.brand.withValues(alpha: 0.3),
                        blurRadius: 10,
                        offset: const Offset(0, 4))
                  ],
                ),
                child: const Icon(Icons.add, color: Colors.white, size: 22),
              ),
            ),
          ],
        ),
        const SizedBox(height: 20),
        _SectionTitle(
            title: '信息频道',
            action: '管理',
            onAction: () => _showManageChannels(context)),
        const SizedBox(height: 12),
        _CategoryGrid(api: api, session: session),
        const SizedBox(height: 20),
        const _SectionTitle(title: '我的待办'),
        const SizedBox(height: 12),
        _TodoList(api: api, session: session),
        const SizedBox(height: 16),
      ],
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle({required this.title, this.action, this.onAction});
  final String title;
  final String? action;
  final VoidCallback? onAction;
  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(title,
            style: const TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w700,
                color: AppTheme.ink)),
        if (action != null)
          GestureDetector(
            onTap: onAction,
            child: Text(action!,
                style: const TextStyle(
                    fontSize: 12.5,
                    color: AppTheme.brandInk,
                    fontWeight: FontWeight.w600)),
          ),
      ],
    );
  }
}

class _CategoryGrid extends StatelessWidget {
  const _CategoryGrid({required this.api, required this.session});
  final CampusApi api;
  final LoginSession session;

  static const _categories = [
    ('教务通知', '已订阅', Icons.format_list_bulleted, AppTheme.brand, '教务'),
    ('课程学术', '已订阅', Icons.article_outlined, AppTheme.info, '课程'),
    ('校园活动', '已订阅', Icons.add, AppTheme.accent, '活动'),
    ('实习招聘', '已订阅', Icons.person_outline, AppTheme.rose, '实习'),
  ];

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Row(
          children: [
            Expanded(child: _buildCard(context, _categories[0])),
            const SizedBox(width: 12),
            Expanded(child: _buildCard(context, _categories[1])),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(child: _buildCard(context, _categories[2])),
            const SizedBox(width: 12),
            Expanded(child: _buildCard(context, _categories[3])),
          ],
        ),
      ],
    );
  }

  Widget _buildCard(
      BuildContext context, (String, String, IconData, Color, String) cat) {
    return GestureDetector(
      onTap: () => Navigator.of(context).push(MaterialPageRoute(
        builder: (_) => CategoryFeedPage(
            label: cat.$1, query: cat.$5, api: api, session: session),
      )),
      child: _CatCard(name: cat.$1, desc: cat.$2, icon: cat.$3, color: cat.$4),
    );
  }
}

class _CatCard extends StatelessWidget {
  const _CatCard(
      {required this.name,
      required this.desc,
      required this.icon,
      required this.color});
  final String name;
  final String desc;
  final IconData icon;
  final Color color;
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(AppTheme.radiusSm),
          border: Border.all(color: AppTheme.line)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
                color: color, borderRadius: BorderRadius.circular(11)),
            child: Icon(icon, color: Colors.white, size: 20),
          ),
          const SizedBox(height: 10),
          Text(name,
              style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                  color: AppTheme.ink)),
          const SizedBox(height: 2),
          Text(desc,
              style: const TextStyle(fontSize: 11.5, color: AppTheme.muted)),
        ],
      ),
    );
  }
}

enum _TodoTab { pending, completed }

class _TodoList extends StatefulWidget {
  const _TodoList({required this.api, required this.session});
  final CampusApi api;
  final LoginSession session;

  @override
  State<_TodoList> createState() => _TodoListState();
}

class _TodoListState extends State<_TodoList> {
  List<ActionItem> _items = [];
  bool _loading = true;
  String? _error;
  _TodoTab _tab = _TodoTab.pending;
  final Set<int> _updating = {};

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
      final items = await widget.api.fetchActions(widget.session);
      if (!mounted) return;
      setState(() {
        _items = items;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = '$e';
        _loading = false;
      });
    }
  }

  Future<void> _openUrl(String? url) async {
    if (url == null) return;
    final uri = Uri.tryParse(url);
    if (uri != null && (uri.scheme == 'http' || uri.scheme == 'https')) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    }
  }

  Future<void> _openDetail(ActionItem item) async {
    try {
      final information = await widget.api
          .fetchInformationDetail(item.informationItemId, widget.session);
      if (!mounted) return;
      await Navigator.of(context).push(MaterialPageRoute(
        builder: (_) => PrototypeDetailPage(
          item: information,
          api: widget.api,
          session: widget.session,
          onItemChanged: (_) {},
        ),
      ));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('详情加载失败，请稍后重试')),
      );
    }
  }

  Future<void> _complete(ActionItem item) async {
    setState(() => _updating.add(item.id));
    try {
      await widget.api.completeAction(item.id, widget.session);
      if (!mounted) return;
      setState(() {
        final index = _items.indexWhere((value) => value.id == item.id);
        if (index >= 0) _items[index] = item.copyWith(status: 'COMPLETED');
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('待办已完成')),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('完成待办失败，请重试')),
      );
    } finally {
      if (mounted) setState(() => _updating.remove(item.id));
    }
  }

  Future<void> _cancel(ActionItem item) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('取消待办？'),
        content: Text('“${item.title}”将从待办列表移除，关联提醒也会取消。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('保留'),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('确认取消'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _updating.add(item.id));
    try {
      await widget.api.cancelAction(item.id, widget.session);
      if (!mounted) return;
      setState(() => _items.removeWhere((value) => value.id == item.id));
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('待办已取消')),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('取消待办失败，请重试')),
      );
    } finally {
      if (mounted) setState(() => _updating.remove(item.id));
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 24),
        child: Center(
          child: SizedBox(
            width: 22,
            height: 22,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        ),
      );
    }
    if (_error != null) {
      return Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(AppTheme.radiusSm),
          border: Border.all(color: AppTheme.line),
        ),
        child: Column(
          children: [
            Text('待办加载失败：$_error',
                style: const TextStyle(fontSize: 13, color: AppTheme.rose)),
            const SizedBox(height: 10),
            TextButton(onPressed: _load, child: const Text('重试')),
          ],
        ),
      );
    }
    final pending = _items.where((item) => !item.isCompleted).toList();
    final completed = _items.where((item) => item.isCompleted).toList();
    final visible = _tab == _TodoTab.pending ? pending : completed;

    return Column(
      children: [
        SizedBox(
          width: double.infinity,
          child: SegmentedButton<_TodoTab>(
            showSelectedIcon: false,
            segments: [
              ButtonSegment(
                  value: _TodoTab.pending,
                  label: Text('待完成 ${pending.length}')),
              ButtonSegment(
                  value: _TodoTab.completed,
                  label: Text('已完成 ${completed.length}')),
            ],
            selected: {_tab},
            onSelectionChanged: (value) => setState(() => _tab = value.first),
          ),
        ),
        const SizedBox(height: 12),
        if (visible.isEmpty)
          _TodoEmpty(completed: _tab == _TodoTab.completed)
        else
          for (var i = 0; i < visible.length; i++) ...[
          if (i > 0) const SizedBox(height: 10),
          _TodoCard(
            item: visible[i],
            updating: _updating.contains(visible[i].id),
            onOpenDetail: () => _openDetail(visible[i]),
            onOpenUrl: _openUrl,
            onComplete: () => _complete(visible[i]),
            onCancel: () => _cancel(visible[i]),
          ),
        ]
      ],
    );
  }
}

class _TodoEmpty extends StatelessWidget {
  const _TodoEmpty({required this.completed});
  final bool completed;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 16),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(AppTheme.radiusSm),
        border: Border.all(color: AppTheme.line),
      ),
      child: Column(
        children: [
          Icon(Icons.task_alt,
              size: 36, color: AppTheme.muted.withValues(alpha: 0.55)),
          const SizedBox(height: 10),
          Text(completed ? '暂无已完成待办' : '暂无待完成事项',
              style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                  color: AppTheme.muted)),
          const SizedBox(height: 4),
          Text(completed ? '完成的事项会保留在这里' : '在信息详情页确认行动后，会出现在这里',
              style: const TextStyle(fontSize: 12, color: AppTheme.muted)),
        ],
      ),
    );
  }
}

class _TodoCard extends StatelessWidget {
  const _TodoCard({
    required this.item,
    required this.updating,
    required this.onOpenDetail,
    required this.onOpenUrl,
    required this.onComplete,
    required this.onCancel,
  });
  final ActionItem item;
  final bool updating;
  final VoidCallback onOpenDetail;
  final void Function(String?) onOpenUrl;
  final VoidCallback onComplete;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    final completed = item.isCompleted;
    final expired = item.isExpired;
    final dueSoon = item.isDueSoon && !completed;
    final muted = completed || expired;
    final statusLabel = completed
        ? '已完成'
        : expired
            ? '已过期'
            : dueSoon
                ? '即将到期'
                : '待完成';
    final statusColor = completed || expired
        ? AppTheme.muted
        : dueSoon
            ? const Color(0xFFBE123C)
            : AppTheme.brandInk;
    final statusBackground = completed || expired
        ? AppTheme.line
        : dueSoon
            ? AppTheme.roseSoft
            : AppTheme.brandSoft;
    final shape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(AppTheme.radiusSm),
      side: BorderSide(
          color: dueSoon ? const Color(0xFFBE123C) : AppTheme.line),
    );

    return Material(
      color: muted ? AppTheme.surface2 : AppTheme.surface,
      shape: shape,
      child: InkWell(
        onTap: onOpenDetail,
        customBorder: shape,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                    decoration: BoxDecoration(
                        color: statusBackground,
                        borderRadius: BorderRadius.circular(5)),
                    child: Text(statusLabel,
                        style: TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.w700,
                            color: statusColor)),
                  ),
                  const SizedBox(width: 8),
                  if (item.sourceName != null)
                    Expanded(
                      child: Text(item.sourceName!,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                              fontSize: 11, color: AppTheme.muted)),
                    )
                  else
                    const Spacer(),
                  const Icon(Icons.chevron_right,
                      size: 17, color: AppTheme.muted),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                item.title,
                style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: muted ? AppTheme.muted : AppTheme.ink,
                    decoration:
                        completed ? TextDecoration.lineThrough : null,
                    height: 1.4),
              ),
              if (item.sourceTitle != null) ...[
                const SizedBox(height: 4),
                Text(item.sourceTitle!,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style:
                        const TextStyle(fontSize: 11.5, color: AppTheme.muted)),
              ],
              if (item.dueAt != null) ...[
                const SizedBox(height: 6),
                Row(
                  children: [
                    Icon(Icons.access_time,
                        size: 13,
                        color: muted
                            ? AppTheme.muted
                            : const Color(0xFFBE123C)),
                    const SizedBox(width: 4),
                    Text(_formatDateTime(item.dueAt!),
                        style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: muted
                                ? AppTheme.muted
                                : const Color(0xFFBE123C))),
                  ],
                ),
              ],
              if (item.requiredMaterials.isNotEmpty) ...[
                const SizedBox(height: 8),
                Wrap(
                  spacing: 6,
                  runSpacing: 4,
                  children: item.requiredMaterials
                      .map((material) => Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 7, vertical: 3),
                            decoration: BoxDecoration(
                                color: AppTheme.line,
                                borderRadius: BorderRadius.circular(5)),
                            child: Text(material,
                                style: const TextStyle(
                                    fontSize: 11, color: AppTheme.ink2)),
                          ))
                      .toList(),
                ),
              ],
              if (item.originalUrl != null && item.originalUrl!.isNotEmpty) ...[
                const SizedBox(height: 8),
                GestureDetector(
                  onTap: () => onOpenUrl(item.originalUrl),
                  child: const Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.open_in_new,
                          size: 13, color: AppTheme.brandInk),
                      SizedBox(width: 4),
                      Text('查看原文',
                          style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                              color: AppTheme.brandInk)),
                    ],
                  ),
                ),
              ],
              if (!completed) ...[
                const SizedBox(height: 10),
                Divider(height: 1, color: AppTheme.line),
                const SizedBox(height: 8),
                Row(
                  children: [
                    TextButton(
                      onPressed: updating ? null : onCancel,
                      child: const Text('取消待办'),
                    ),
                    const Spacer(),
                    FilledButton.icon(
                      onPressed: updating ? null : onComplete,
                      icon: updating
                          ? const SizedBox(
                              width: 14,
                              height: 14,
                              child: CircularProgressIndicator(
                                  strokeWidth: 2, color: Colors.white),
                            )
                          : const Icon(Icons.check, size: 16),
                      label: const Text('完成'),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  static String _formatDateTime(DateTime dt) {
    return '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')} '
        '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }
}

class _SearchPage extends StatefulWidget {
  const _SearchPage({required this.api, required this.session});
  final CampusApi api;
  final LoginSession session;
  @override
  State<_SearchPage> createState() => _SearchPageState();
}

class _SearchPageState extends State<_SearchPage> {
  final _ctrl = TextEditingController();
  final _focusNode = FocusNode();
  List<SearchResultItem> _results = [];
  bool _loading = false;
  String? _error;
  int _total = 0;
  String? _searchNotice;
  bool _fallback = false;
  List<String> _history = [];

  static const _historyKey = 'search_history';
  static const _maxHistory = 12;

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  @override
  void dispose() {
    _ctrl.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  Future<void> _loadHistory() async {
    final prefs = await SharedPreferences.getInstance();
    if (!mounted) return;
    setState(() {
      _history = prefs.getStringList(_historyKey) ?? [];
    });
  }

  Future<void> _saveToHistory(String query) async {
    final prefs = await SharedPreferences.getInstance();
    final list = List<String>.from(_history);
    list.remove(query);
    list.insert(0, query);
    if (list.length > _maxHistory) list.removeRange(_maxHistory, list.length);
    if (!mounted) return;
    setState(() => _history = list);
    await prefs.setStringList(_historyKey, list);
  }

  Future<void> _clearHistory() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_historyKey);
    if (!mounted) return;
    setState(() => _history = []);
  }

  void _useHistory(String query) {
    _ctrl.text = query;
    _doSearch();
  }

  Future<void> _doSearch() async {
    final q = _ctrl.text.trim();
    if (q.isEmpty) return;
    await _saveToHistory(q);
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = await widget.api.search(q, widget.session);
      if (!mounted) return;
      setState(() {
        _results = result.items;
        _total = result.total;
        _searchNotice = result.message;
        _fallback = result.fallback;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = '$e';
        _loading = false;
      });
    }
  }

  Widget _buildHistory() {
    if (_history.isEmpty) {
      return const Center(
        child: Text('输入关键词开始搜索',
            style: TextStyle(fontSize: 14, color: AppTheme.muted)),
      );
    }
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('搜索历史',
                  style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: AppTheme.ink2)),
              GestureDetector(
                onTap: _clearHistory,
                child: const Text('清除',
                    style: TextStyle(
                        fontSize: 12, color: AppTheme.muted)),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: _history
                .map((q) => GestureDetector(
                      onTap: () => _useHistory(q),
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 12, vertical: 7),
                        decoration: BoxDecoration(
                          color: AppTheme.surface,
                          borderRadius: BorderRadius.circular(999),
                          border: Border.all(color: AppTheme.line),
                        ),
                        child: Text(q,
                            style: const TextStyle(
                                fontSize: 13,
                                color: AppTheme.ink2,
                                fontWeight: FontWeight.w500)),
                      ),
                    ))
                .toList(),
          ),
        ],
      ),
    );
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
                  border: Border(bottom: BorderSide(color: AppTheme.line))),
              child: Row(
                children: [
                  GestureDetector(
                    onTap: () => Navigator.of(context).pop(),
                    child: const Icon(Icons.arrow_back_ios_new,
                        size: 18, color: AppTheme.ink2),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: TextField(
                      controller: _ctrl,
                      focusNode: _focusNode,
                      autofocus: true,
                      textInputAction: TextInputAction.search,
                      onSubmitted: (_) => _doSearch(),
                      style: const TextStyle(fontSize: 14, color: AppTheme.ink),
                      decoration: const InputDecoration(
                        border: InputBorder.none,
                        hintText: '搜索通知、讲座、活动…',
                        hintStyle:
                            TextStyle(fontSize: 14, color: AppTheme.muted),
                        isDense: true,
                      ),
                    ),
                  ),
                  GestureDetector(
                    onTap: _doSearch,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 12, vertical: 7),
                      decoration: BoxDecoration(
                          color: AppTheme.brand,
                          borderRadius: BorderRadius.circular(8)),
                      child: const Text('搜索',
                          style: TextStyle(
                              fontSize: 12.5,
                              fontWeight: FontWeight.w700,
                              color: Colors.white)),
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: _loading
                  ? const Center(
                      child: CircularProgressIndicator(color: AppTheme.brand))
                  : _error != null
                      ? Center(
                          child: Text('搜索失败：$_error',
                              style: const TextStyle(color: AppTheme.rose)))
                      : _results.isEmpty
                          ? _buildHistory()
                          : ListView.builder(
                              padding: const EdgeInsets.all(16),
                              itemCount: _results.length + 1,
                              itemBuilder: (ctx, i) {
                                if (i == 0) {
                                  return Padding(
                                    padding: const EdgeInsets.only(bottom: 12),
                                    child: Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        children: [
                                          if (_fallback)
                                            Container(
                                              width: double.infinity,
                                              margin: const EdgeInsets.only(
                                                  bottom: 10),
                                              padding: const EdgeInsets.all(10),
                                              decoration: BoxDecoration(
                                                  color:
                                                      const Color(0xFFFFF4D8),
                                                  borderRadius:
                                                      BorderRadius.circular(8)),
                                              child: Row(
                                                children: [
                                                  const Icon(Icons.swap_horiz,
                                                      size: 16,
                                                      color: Color(0xFF7A5200)),
                                                  const SizedBox(width: 6),
                                                  Expanded(
                                                    child: Text(
                                                        'AI 搜索暂不可用，已切换关键词搜索。${_searchNotice == null ? '' : ' $_searchNotice'}',
                                                        style: const TextStyle(
                                                            fontSize: 12,
                                                            color:
                                                                Color(0xFF7A5200))),
                                                  ),
                                                ],
                                              ),
                                            ),
                                          Text('找到 $_total 条结果',
                                              style: const TextStyle(
                                                  fontSize: 12.5,
                                                  color: AppTheme.muted)),
                                        ]),
                                  );
                                }
                                final item = _results[i - 1];
                                return Container(
                                  margin: const EdgeInsets.only(bottom: 12),
                                  padding: const EdgeInsets.all(14),
                                  decoration: BoxDecoration(
                                    color: AppTheme.surface,
                                    borderRadius: BorderRadius.circular(
                                        AppTheme.radiusSm),
                                    border: Border.all(color: AppTheme.line),
                                  ),
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      if (item.sourceName != null) ...[
                                        Container(
                                          padding: const EdgeInsets.symmetric(
                                              horizontal: 7, vertical: 2),
                                          decoration: BoxDecoration(
                                              color: AppTheme.brandSoft,
                                              borderRadius:
                                                  BorderRadius.circular(5)),
                                          child: Text(item.sourceName!,
                                              style: const TextStyle(
                                                  fontSize: 10,
                                                  fontWeight: FontWeight.w600,
                                                  color: AppTheme.brandInk)),
                                        ),
                                        const SizedBox(height: 6),
                                      ],
                                      Text(item.title,
                                          style: const TextStyle(
                                              fontSize: 14,
                                              fontWeight: FontWeight.w700,
                                              color: AppTheme.ink,
                                              height: 1.4)),
                                      if (item.snippet.isNotEmpty) ...[
                                        const SizedBox(height: 6),
                                        Text(item.snippet,
                                            maxLines: 3,
                                            overflow: TextOverflow.ellipsis,
                                            style: const TextStyle(
                                                fontSize: 12.5,
                                                color: AppTheme.ink2,
                                                height: 1.5)),
                                      ],
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
}

// —————————————————————————————————————————
// 分类详情页：展示某个分类下的信息列表
// —————————————————————————————————————————

class CategoryFeedPage extends StatefulWidget {
  const CategoryFeedPage({
    super.key,
    required this.label,
    required this.query,
    required this.api,
    required this.session,
  });
  final String label;
  final String query;
  final CampusApi api;
  final LoginSession session;

  @override
  State<CategoryFeedPage> createState() => _CategoryFeedPageState();
}

class _CategoryFeedPageState extends State<CategoryFeedPage> {
  List<SearchResultItem> _results = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final results = await widget.api.search(widget.query, widget.session);
      if (!mounted) return;
      setState(() {
        _results = results.items;
        _error = null;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = '分类信息加载失败：$error';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      appBar: AppBar(
        backgroundColor: AppTheme.bg,
        elevation: 0,
        title: Text(widget.label,
            style: const TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w800,
                color: AppTheme.ink)),
        iconTheme: const IconThemeData(color: AppTheme.ink),
      ),
      body: _loading
          ? const Center(
              child: SizedBox(
                  width: 24,
                  height: 24,
                  child: CircularProgressIndicator(strokeWidth: 2)))
          : _error != null
              ? Center(
                  child: Text(_error!,
                      style: const TextStyle(color: AppTheme.muted)))
              : _results.isEmpty
                  ? const Center(
                      child: Text('暂无相关内容',
                          style:
                              TextStyle(fontSize: 14, color: AppTheme.muted)))
                  : ListView.separated(
                      padding: const EdgeInsets.fromLTRB(20, 10, 20, 24),
                      itemCount: _results.length,
                      separatorBuilder: (_, __) => const SizedBox(height: 12),
                      itemBuilder: (ctx, i) {
                        final r = _results[i];
                        return GestureDetector(
                          onTap: () =>
                              Navigator.of(context).push(MaterialPageRoute(
                            builder: (_) => PrototypeDetailPage(
                                item: r.toInformationItem(),
                                api: widget.api,
                                session: widget.session,
                                onItemChanged: (_) {}),
                          )),
                          child: Container(
                            padding: const EdgeInsets.all(14),
                            decoration: BoxDecoration(
                              color: AppTheme.surface,
                              borderRadius: BorderRadius.circular(14),
                              border: Border.all(color: AppTheme.line),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    Container(
                                      padding: const EdgeInsets.symmetric(
                                          horizontal: 7, vertical: 3),
                                      decoration: BoxDecoration(
                                          color: const Color(0xFFF3F4F6),
                                          borderRadius:
                                              BorderRadius.circular(4)),
                                      child: Text(r.sourceName ?? '未知来源',
                                          style: const TextStyle(
                                              fontSize: 10.5,
                                              fontWeight: FontWeight.w700,
                                              color: AppTheme.muted)),
                                    ),
                                    const Spacer(),
                                    if (r.eventType != null)
                                      Container(
                                        padding: const EdgeInsets.symmetric(
                                            horizontal: 7, vertical: 3),
                                        decoration: BoxDecoration(
                                            color: AppTheme.brandSoft,
                                            borderRadius:
                                                BorderRadius.circular(4)),
                                        child: Text(r.eventType!,
                                            style: const TextStyle(
                                                fontSize: 10.5,
                                                fontWeight: FontWeight.w700,
                                                color: AppTheme.brandInk)),
                                      ),
                                  ],
                                ),
                                const SizedBox(height: 8),
                                Text(r.title,
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(
                                        fontSize: 15,
                                        fontWeight: FontWeight.w700,
                                        color: AppTheme.ink,
                                        height: 1.4)),
                                const SizedBox(height: 6),
                                Text(r.snippet,
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(
                                        fontSize: 12.5,
                                        color: AppTheme.muted,
                                        height: 1.5)),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
    );
  }
}
