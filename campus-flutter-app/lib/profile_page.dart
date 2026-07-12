import 'package:flutter/material.dart';
import 'app_theme.dart';
import 'information_api.dart';
import 'my_subscriptions_page.dart';
import 'my_favorites_page.dart';
import 'my_read_history_page.dart';
import 'my_imported_events_page.dart';

class PrototypeProfilePage extends StatefulWidget {
  const PrototypeProfilePage({
    super.key,
    required this.userName,
    required this.onLogout,
    required this.api,
    required this.session,
  });
  final String userName;
  final VoidCallback onLogout;
  final CampusApi api;
  final LoginSession session;

  @override
  State<PrototypeProfilePage> createState() => _PrototypeProfilePageState();
}

class _PrototypeProfilePageState extends State<PrototypeProfilePage> {
  UserStats _stats = const UserStats(readCount: 0, favoriteCount: 0, subscriptionCount: 0);
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _loadStats();
  }

  Future<void> _loadStats() async {
    try {
      final stats = await widget.api.fetchStats(widget.session);
      if (!mounted) return;
      setState(() { _stats = stats; _loading = false; });
    } catch (_) {
      if (!mounted) return;
      setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 6, 20, 28),
      children: [
        // Profile header
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(AppTheme.radius),
            gradient: AppTheme.brandDarkGradient,
          ),
          child: Row(
            children: [
              Container(
                width: 58, height: 58,
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(18),
                ),
                child: Center(
                  child: Text(widget.userName.isNotEmpty ? widget.userName[0] : '我',
                    style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: Colors.white)),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(widget.userName, style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w800, color: Colors.white)),
                    const SizedBox(height: 3),
                    Text('计算机学院 · 学号 20231104', style: TextStyle(fontSize: 12, color: Colors.white.withValues(alpha: 0.9))),
                  ],
                ),
              ),
              Container(
                width: 34, height: 34,
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: const Icon(Icons.edit, color: Colors.white, size: 16),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        // Stats
        Container(
          decoration: BoxDecoration(color: AppTheme.surface, borderRadius: BorderRadius.circular(AppTheme.radiusSm), border: Border.all(color: AppTheme.line)),
          child: _loading
              ? const Padding(
                  padding: EdgeInsets.symmetric(vertical: 24),
                  child: Center(child: SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2))),
                )
              : Row(
                  children: [
                    _StatBox(value: '${_stats.readCount}', label: '已读', showBorder: true),
                    _StatBox(value: '${_stats.favoriteCount}', label: '收藏', showBorder: true),
                    _StatBox(value: '${_stats.subscriptionCount}', label: '关注源'),
                  ],
                ),
        ),
        const SizedBox(height: 16),
        // AI Profile
        Container(
          padding: const EdgeInsets.all(15),
          decoration: BoxDecoration(color: AppTheme.surface, borderRadius: BorderRadius.circular(AppTheme.radiusSm), border: Border.all(color: AppTheme.line)),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(Icons.auto_awesome, size: 18, color: AppTheme.brandInk),
                  const SizedBox(width: 8),
                  const Text('AI 为你生成的信息画像', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700, color: AppTheme.ink)),
                  const SizedBox(width: 6),
                  const Text('· 可调整', style: TextStyle(fontSize: 11, color: AppTheme.muted)),
                ],
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8, runSpacing: 8,
                children: [
                  _TagChip(label: '课程学术', active: true),
                  _TagChip(label: '校园活动', active: true),
                  _TagChip(label: '实习招聘', active: true),
                  _TagChip(label: '失物招领'),
                  _TagChip(label: '后勤服务'),
                ],
              ),
              const SizedBox(height: 14),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('重要性敏感度', style: TextStyle(fontSize: 12.5, color: AppTheme.ink2)),
                  Row(
                    children: [
                      const Text('高', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: AppTheme.brandInk)),
                      const SizedBox(width: 10),
                      Container(
                        width: 120, height: 6,
                        decoration: BoxDecoration(color: AppTheme.surface2, borderRadius: BorderRadius.circular(99)),
                        child: FractionallySizedBox(
                          alignment: Alignment.centerLeft,
                          widthFactor: 0.78,
                          child: Container(
                            decoration: BoxDecoration(color: AppTheme.brand, borderRadius: BorderRadius.circular(99)),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        // Menu
        Container(
          decoration: BoxDecoration(color: AppTheme.surface, borderRadius: BorderRadius.circular(AppTheme.radiusSm), border: Border.all(color: AppTheme.line)),
          child: Column(
            children: [
              _MenuItem(
                icon: Icons.format_list_bulleted,
                label: '我的订阅',
                onTap: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => MySubscriptionsPage(api: widget.api, session: widget.session),
                )),
              ),
              _MenuItem(
                icon: Icons.school_outlined,
                label: '我的雨课堂导入',
                onTap: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => MyImportedEventsPage(api: widget.api, session: widget.session),
                )),
              ),
              _MenuItem(
                icon: Icons.bookmark_outline,
                label: '收藏夹',
                onTap: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => MyFavoritesPage(api: widget.api, session: widget.session, onOpenDetail: _openDetail),
                )),
              ),
              _MenuItem(
                icon: Icons.access_time,
                label: '阅读历史',
                onTap: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => MyReadHistoryPage(api: widget.api, session: widget.session, onOpenDetail: _openDetail),
                )),
              ),
              _MenuItem(icon: Icons.notifications_outlined, label: '推送与提醒设置'),
              _MenuItem(icon: Icons.info_outline, label: '数据源偏好'),
              _MenuItem(icon: Icons.logout, label: '退出登录', isLogout: true, onTap: widget.onLogout),
            ],
          ),
        ),
      ],
    );
  }

  void _openDetail(InformationItem item) {
    // 复用 detail 页面（通过 Navigator 打开）
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => _SimpleDetailWrapper(item: item, api: widget.api, session: widget.session),
    ));
  }
}

/// 简易详情页包装（在 profile 子页面中复用）
class _SimpleDetailWrapper extends StatefulWidget {
  const _SimpleDetailWrapper({required this.item, required this.api, required this.session});
  final InformationItem item;
  final CampusApi api;
  final LoginSession session;

  @override
  State<_SimpleDetailWrapper> createState() => _SimpleDetailWrapperState();
}

class _SimpleDetailWrapperState extends State<_SimpleDetailWrapper> {
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
    } catch (_) {}
  }

  Future<void> _toggleFav() async {
    final newStatus = _fav ? 'READ' : 'FAVORITED';
    try {
      final item = await widget.api.updateReadStatus(_item.id, newStatus, widget.session);
      if (!mounted) return;
      setState(() { _item = item; _fav = !_fav; });
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
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                GestureDetector(
                  onTap: () => Navigator.of(context).pop(),
                  child: Container(
                    width: 36, height: 36,
                    decoration: BoxDecoration(color: AppTheme.surface, borderRadius: BorderRadius.circular(11), border: Border.all(color: AppTheme.line)),
                    child: const Icon(Icons.arrow_back_ios_new, size: 17, color: AppTheme.ink2),
                  ),
                ),
                GestureDetector(
                  onTap: _toggleFav,
                  child: Container(
                    width: 36, height: 36,
                    decoration: BoxDecoration(
                      color: _fav ? AppTheme.brandSoft : AppTheme.surface,
                      borderRadius: BorderRadius.circular(11),
                      border: Border.all(color: _fav ? AppTheme.brand : AppTheme.line),
                    ),
                    child: Icon(_fav ? Icons.bookmark : Icons.bookmark_outline, size: 17, color: _fav ? AppTheme.brand : AppTheme.ink2),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Text(_item.title, style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w800, color: AppTheme.ink, height: 1.35)),
            const SizedBox(height: 12),
            Row(children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(color: AppTheme.brandSoft, borderRadius: BorderRadius.circular(6)),
                child: Text(_item.sourceName, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: AppTheme.brandInk)),
              ),
              const SizedBox(width: 8),
              Text(_item.displayTime, style: const TextStyle(fontSize: 12, color: AppTheme.muted)),
            ]),
            const SizedBox(height: 16),
            Text(
              _item.detailContent.isNotEmpty ? _item.detailContent : _item.preview,
              style: const TextStyle(fontSize: 13, color: AppTheme.ink2, height: 1.7),
            ),
          ],
        ),
      ),
    );
  }
}

class _StatBox extends StatelessWidget {
  const _StatBox({required this.value, required this.label, this.showBorder = false});
  final String value;
  final String label;
  final bool showBorder;
  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 14),
        decoration: BoxDecoration(
          border: showBorder ? const Border(right: BorderSide(color: AppTheme.line)) : null,
        ),
        child: Column(
          children: [
            Text(value, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: AppTheme.ink)),
            const SizedBox(height: 2),
            Text(label, style: const TextStyle(fontSize: 11, color: AppTheme.muted)),
          ],
        ),
      ),
    );
  }
}

class _TagChip extends StatelessWidget {
  const _TagChip({required this.label, this.active = false});
  final String label;
  final bool active;
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 6),
      decoration: BoxDecoration(
        color: active ? AppTheme.brandSoft : AppTheme.surface2,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(label, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: active ? AppTheme.brandInk : AppTheme.ink2)),
    );
  }
}

class _MenuItem extends StatelessWidget {
  const _MenuItem({required this.icon, required this.label, this.isLogout = false, this.onTap});
  final IconData icon;
  final String label;
  final bool isLogout;
  final VoidCallback? onTap;
  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 14),
        decoration: BoxDecoration(
          border: isLogout ? null : const Border(bottom: BorderSide(color: AppTheme.line)),
        ),
        child: Row(
          children: [
            Container(
              width: 30, height: 30,
              decoration: BoxDecoration(
                color: isLogout ? AppTheme.roseSoft : AppTheme.surface2,
                borderRadius: BorderRadius.circular(9),
              ),
              child: Icon(icon, size: 16, color: isLogout ? AppTheme.rose : AppTheme.brandInk),
            ),
            const SizedBox(width: 12),
            Expanded(child: Text(label, style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: isLogout ? AppTheme.rose : AppTheme.ink))),
            Icon(Icons.chevron_right, color: AppTheme.muted, size: 20),
          ],
        ),
      ),
    );
  }
}
