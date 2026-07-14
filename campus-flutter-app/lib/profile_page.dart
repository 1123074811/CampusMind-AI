import 'package:flutter/material.dart';
import 'dart:convert';
import 'package:share_plus/share_plus.dart';
import 'app_theme.dart';
import 'information_api.dart';
import 'my_subscriptions_page.dart';
import 'my_favorites_page.dart';
import 'my_read_history_page.dart';
import 'my_imported_events_page.dart';
import 'my_actions_page.dart';
import 'reminders_page.dart';
import 'data_source_preference_page.dart';

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
  UserStats _stats =
      const UserStats(readCount: 0, favoriteCount: 0, subscriptionCount: 0);
  UserProfile? _profile;
  bool _loading = true;
  final List<String> _allTags = [
    '课程学术',
    '校园活动',
    '实习招聘',
    '失物招领',
    '后勤服务',
    '教务通知',
    '竞赛比赛',
    '生活服务'
  ];
  Set<String> _activeTags = {};
  double _sensitivity = 0.5;
  PrivacyStatus? _privacy;

  @override
  void initState() {
    super.initState();
    _loadStats();
    _loadProfile();
    _loadTags();
    _loadPrivacy();
  }

  Future<void> _loadPrivacy() async {
    try {
      final privacy = await widget.api.fetchPrivacyStatus(widget.session);
      if (mounted) setState(() => _privacy = privacy);
    } catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('隐私设置加载失败：$error')));
    }
  }

  Future<void> _showPrivacySettings() async {
    var personalization = _privacy?.consents['PERSONALIZATION'] ?? true;
    var notifications = _privacy?.consents['NOTIFICATION'] ?? true;
    await showDialog<void>(context: context, builder: (context) => StatefulBuilder(builder: (context, setDialogState) => AlertDialog(
      title: const Text('隐私与授权'),
      content: Column(mainAxisSize: MainAxisSize.min, children: [
        Text('当前政策版本：${_privacy?.policyVersion ?? '加载中'}\n运营数据保留：${_privacy?.retentionDays ?? 365} 天'),
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: const Text('个性化画像与推荐'),
          value: personalization,
          onChanged: (value) => setDialogState(() => personalization = value),
        ),
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: const Text('消息通知'),
          value: notifications,
          onChanged: (value) => setDialogState(() => notifications = value),
        ),
      ]),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('取消')),
        FilledButton(onPressed: () async {
          try {
            final version = _privacy?.policyVersion ?? '2026-07-01';
            await widget.api.updateConsent('PERSONALIZATION', personalization, version, widget.session);
            final updated = await widget.api.updateConsent('NOTIFICATION', notifications, version, widget.session);
            if (!context.mounted) return;
            Navigator.pop(context);
            if (mounted) setState(() => _privacy = updated);
          } catch (error) {
            if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('授权更新失败：$error')));
          }
        }, child: const Text('保存')),
      ],
    )));
  }

  Future<void> _loadTags() async {
    try {
      final profile = await widget.api.fetchProfileTags(widget.session);
      if (!mounted) return;
      setState(() {
        _activeTags = profile.tags.toSet();
        _sensitivity = profile.sensitivity;
        for (final tag in profile.tags) {
          if (!_allTags.contains(tag)) _allTags.add(tag);
        }
      });
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('画像偏好加载失败，未使用虚拟数据')),
      );
    }
  }

  Future<void> _saveTags() async {
    try {
      await widget.api.updateProfileTags(
          _activeTags.toList(), _sensitivity, widget.session);
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('画像偏好保存失败：$error')));
    }
  }

  Future<void> _loadStats() async {
    try {
      final stats = await widget.api.fetchStats(widget.session);
      if (!mounted) return;
      setState(() {
        _stats = stats;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _loading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('用户统计加载失败')),
      );
    }
  }

  Future<void> _loadProfile() async {
    try {
      final profile = await widget.api.fetchMe(widget.session);
      if (!mounted) return;
      setState(() => _profile = profile);
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('个人资料加载失败')),
      );
    }
  }

  Future<void> _exportData() async {
    try {
      final data = await widget.api.exportMyData(widget.session);
      await SharePlus.instance.share(
        ShareParams(
          text: const JsonEncoder.withIndent('  ').convert(data),
          subject: 'CampusMind 个人数据导出',
        ),
      );
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('个人数据导出失败：$error')));
    }
  }

  Future<void> _deleteAccount() async {
    final controller = TextEditingController();
    final password = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('注销账号'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          const Text('注销会清除个人画像、收藏、订阅、行动和私有事件，并匿名化账号。请输入密码确认。'),
          const SizedBox(height: 12),
          TextField(
              controller: controller,
              obscureText: true,
              decoration: const InputDecoration(labelText: '当前密码')),
        ]),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context), child: const Text('取消')),
          FilledButton(
              onPressed: () => Navigator.pop(context, controller.text),
              child: const Text('确认注销')),
        ],
      ),
    );
    controller.dispose();
    if (password == null || password.isEmpty) return;
    try {
      await widget.api.deleteMyAccount(password, widget.session);
      if (!mounted) return;
      widget.onLogout();
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('账号未注销：$error')));
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
                width: 58,
                height: 58,
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(18),
                ),
                child: Center(
                  child: Text(
                      widget.userName.isNotEmpty ? widget.userName[0] : '我',
                      style: const TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.w800,
                          color: Colors.white)),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(widget.userName,
                        style: const TextStyle(
                            fontSize: 19,
                            fontWeight: FontWeight.w800,
                            color: Colors.white)),
                    const SizedBox(height: 3),
                    Text(
                        _profile?.nickname ??
                            _profile?.email ??
                            _profile?.role ??
                            '加载中…',
                        style: TextStyle(
                            fontSize: 12,
                            color: Colors.white.withValues(alpha: 0.9))),
                  ],
                ),
              ),
              Container(
                width: 34,
                height: 34,
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
          decoration: BoxDecoration(
              color: AppTheme.surface,
              borderRadius: BorderRadius.circular(AppTheme.radiusSm),
              border: Border.all(color: AppTheme.line)),
          child: _loading
              ? const Padding(
                  padding: EdgeInsets.symmetric(vertical: 24),
                  child: Center(
                      child: SizedBox(
                          width: 24,
                          height: 24,
                          child: CircularProgressIndicator(strokeWidth: 2))),
                )
              : Row(
                  children: [
                    _StatBox(
                        value: '${_stats.readCount}',
                        label: '已读',
                        showBorder: true),
                    _StatBox(
                        value: '${_stats.favoriteCount}',
                        label: '收藏',
                        showBorder: true),
                    _StatBox(
                        value: '${_stats.subscriptionCount}', label: '关注源'),
                  ],
                ),
        ),
        const SizedBox(height: 16),
        // AI Profile
        Container(
          padding: const EdgeInsets.all(15),
          decoration: BoxDecoration(
              color: AppTheme.surface,
              borderRadius: BorderRadius.circular(AppTheme.radiusSm),
              border: Border.all(color: AppTheme.line)),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(Icons.auto_awesome,
                      size: 18, color: AppTheme.brandInk),
                  const SizedBox(width: 8),
                  const Text('AI 为你生成的信息画像',
                      style: TextStyle(
                          fontSize: 13.5,
                          fontWeight: FontWeight.w700,
                          color: AppTheme.ink)),
                  const SizedBox(width: 6),
                  const Text('· 可调整',
                      style: TextStyle(fontSize: 11, color: AppTheme.muted)),
                ],
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: _allTags.map((tag) {
                  final active = _activeTags.contains(tag);
                  return GestureDetector(
                    onTap: () {
                      setState(() {
                        if (active) {
                          _activeTags.remove(tag);
                        } else {
                          _activeTags.add(tag);
                        }
                      });
                      _saveTags();
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 11, vertical: 6),
                      decoration: BoxDecoration(
                        color: active ? AppTheme.brandSoft : AppTheme.surface2,
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                            color:
                                active ? AppTheme.brand : Colors.transparent),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (active) ...[
                            const Icon(Icons.check,
                                size: 13, color: AppTheme.brandInk),
                            const SizedBox(width: 4)
                          ],
                          Text(tag,
                              style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                  color: active
                                      ? AppTheme.brandInk
                                      : AppTheme.ink2)),
                        ],
                      ),
                    ),
                  );
                }).toList(),
              ),
              const SizedBox(height: 14),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('重要性敏感度',
                      style: TextStyle(fontSize: 12.5, color: AppTheme.ink2)),
                  Row(
                    children: [
                      Text(
                          _sensitivity >= 0.7
                              ? '高'
                              : _sensitivity >= 0.4
                                  ? '中'
                                  : '低',
                          style: const TextStyle(
                              fontSize: 12.5,
                              fontWeight: FontWeight.w700,
                              color: AppTheme.brandInk)),
                      const SizedBox(width: 10),
                      SizedBox(
                        width: 120,
                        child: SliderTheme(
                          data: SliderThemeData(
                            trackHeight: 6,
                            activeTrackColor: AppTheme.brand,
                            inactiveTrackColor: AppTheme.surface2,
                            thumbColor: AppTheme.brand,
                            thumbShape: const RoundSliderThumbShape(
                                enabledThumbRadius: 8),
                            overlayShape: SliderComponentShape.noOverlay,
                          ),
                          child: Slider(
                            value: _sensitivity,
                            onChanged: (v) {
                              setState(() => _sensitivity = v);
                            },
                            onChangeEnd: (_) => _saveTags(),
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
          decoration: BoxDecoration(
              color: AppTheme.surface,
              borderRadius: BorderRadius.circular(AppTheme.radiusSm),
              border: Border.all(color: AppTheme.line)),
          child: Column(
            children: [
              _MenuItem(
                icon: Icons.format_list_bulleted,
                label: '我的订阅',
                onTap: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => MySubscriptionsPage(
                      api: widget.api, session: widget.session),
                )),
              ),
              _MenuItem(
                icon: Icons.task_alt,
                label: '我的行动',
                onTap: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) =>
                      MyActionsPage(api: widget.api, session: widget.session),
                )),
              ),
              _MenuItem(
                icon: Icons.school_outlined,
                label: '我的雨课堂导入',
                onTap: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => MyImportedEventsPage(
                      api: widget.api, session: widget.session),
                )),
              ),
              _MenuItem(
                icon: Icons.bookmark_outline,
                label: '收藏夹',
                onTap: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => MyFavoritesPage(
                      api: widget.api,
                      session: widget.session,
                      onOpenDetail: _openDetail),
                )),
              ),
              _MenuItem(
                icon: Icons.access_time,
                label: '阅读历史',
                onTap: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => MyReadHistoryPage(
                      api: widget.api,
                      session: widget.session,
                      onOpenDetail: _openDetail),
                )),
              ),
              _MenuItem(
                icon: Icons.notifications_outlined,
                label: '消息提醒',
                onTap: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) =>
                      RemindersPage(api: widget.api, session: widget.session),
                )),
              ),
              _MenuItem(
                icon: Icons.info_outline,
                label: '数据源偏好',
                onTap: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => DataSourcePreferencePage(
                      api: widget.api, session: widget.session),
                )),
              ),
              _MenuItem(
                  icon: Icons.privacy_tip_outlined,
                  label: '隐私与授权',
                  onTap: _showPrivacySettings),
              _MenuItem(
                  icon: Icons.download_outlined,
                  label: '导出个人数据',
                  onTap: _exportData),
              _MenuItem(
                  icon: Icons.person_off_outlined,
                  label: '注销账号',
                  isLogout: true,
                  onTap: _deleteAccount),
              _MenuItem(
                  icon: Icons.logout,
                  label: '退出登录',
                  isLogout: true,
                  onTap: widget.onLogout),
            ],
          ),
        ),
      ],
    );
  }

  void _openDetail(InformationItem item) {
    // 复用 detail 页面（通过 Navigator 打开）
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => _SimpleDetailWrapper(
          item: item, api: widget.api, session: widget.session),
    ));
  }
}

/// 简易详情页包装（在 profile 子页面中复用）
class _SimpleDetailWrapper extends StatefulWidget {
  const _SimpleDetailWrapper(
      {required this.item, required this.api, required this.session});
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
      var item =
          await widget.api.fetchInformationDetail(_item.id, widget.session);
      if (item.readStatus == 'NEW') {
        try {
          item = await widget.api
              .updateReadStatus(item.id, 'READ', widget.session);
        } catch (_) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('已打开正文，但阅读状态暂未同步')),
            );
          }
        }
      }
      if (!mounted) return;
      setState(() => _item = item);
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('详情加载失败：$error')));
    }
  }

  Future<void> _toggleFav() async {
    final newStatus = _fav ? 'READ' : 'FAVORITED';
    try {
      final item = await widget.api
          .updateReadStatus(_item.id, newStatus, widget.session);
      if (!mounted) return;
      setState(() {
        _item = item;
        _fav = !_fav;
      });
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('收藏状态未改变：$error')));
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
                    width: 36,
                    height: 36,
                    decoration: BoxDecoration(
                        color: AppTheme.surface,
                        borderRadius: BorderRadius.circular(11),
                        border: Border.all(color: AppTheme.line)),
                    child: const Icon(Icons.arrow_back_ios_new,
                        size: 17, color: AppTheme.ink2),
                  ),
                ),
                GestureDetector(
                  onTap: _toggleFav,
                  child: Container(
                    width: 36,
                    height: 36,
                    decoration: BoxDecoration(
                      color: _fav ? AppTheme.brandSoft : AppTheme.surface,
                      borderRadius: BorderRadius.circular(11),
                      border: Border.all(
                          color: _fav ? AppTheme.brand : AppTheme.line),
                    ),
                    child: Icon(_fav ? Icons.bookmark : Icons.bookmark_outline,
                        size: 17, color: _fav ? AppTheme.brand : AppTheme.ink2),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Text(_item.title,
                style: const TextStyle(
                    fontSize: 21,
                    fontWeight: FontWeight.w800,
                    color: AppTheme.ink,
                    height: 1.35)),
            const SizedBox(height: 12),
            Row(children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                    color: AppTheme.brandSoft,
                    borderRadius: BorderRadius.circular(6)),
                child: Text(_item.sourceName,
                    style: const TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: AppTheme.brandInk)),
              ),
              const SizedBox(width: 8),
              Text(_item.displayTime,
                  style: const TextStyle(fontSize: 12, color: AppTheme.muted)),
            ]),
            const SizedBox(height: 16),
            Text(
              _item.detailContent.isNotEmpty
                  ? _item.detailContent
                  : _item.preview,
              style: const TextStyle(
                  fontSize: 13, color: AppTheme.ink2, height: 1.7),
            ),
          ],
        ),
      ),
    );
  }
}

class _StatBox extends StatelessWidget {
  const _StatBox(
      {required this.value, required this.label, this.showBorder = false});
  final String value;
  final String label;
  final bool showBorder;
  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 14),
        decoration: BoxDecoration(
          border: showBorder
              ? const Border(right: BorderSide(color: AppTheme.line))
              : null,
        ),
        child: Column(
          children: [
            Text(value,
                style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
                    color: AppTheme.ink)),
            const SizedBox(height: 2),
            Text(label,
                style: const TextStyle(fontSize: 11, color: AppTheme.muted)),
          ],
        ),
      ),
    );
  }
}

class _MenuItem extends StatelessWidget {
  const _MenuItem(
      {required this.icon,
      required this.label,
      this.isLogout = false,
      this.onTap});
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
          border: isLogout
              ? null
              : const Border(bottom: BorderSide(color: AppTheme.line)),
        ),
        child: Row(
          children: [
            Container(
              width: 30,
              height: 30,
              decoration: BoxDecoration(
                color: isLogout ? AppTheme.roseSoft : AppTheme.surface2,
                borderRadius: BorderRadius.circular(9),
              ),
              child: Icon(icon,
                  size: 16,
                  color: isLogout ? AppTheme.rose : AppTheme.brandInk),
            ),
            const SizedBox(width: 12),
            Expanded(
                child: Text(label,
                    style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: isLogout ? AppTheme.rose : AppTheme.ink))),
            Icon(Icons.chevron_right, color: AppTheme.muted, size: 20),
          ],
        ),
      ),
    );
  }
}
