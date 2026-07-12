import 'package:flutter/material.dart';
import 'app_theme.dart';

class PrototypeProfilePage extends StatelessWidget {
  const PrototypeProfilePage({super.key, required this.userName, required this.onLogout});
  final String userName;
  final VoidCallback onLogout;

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
                  child: Text(userName.isNotEmpty ? userName[0] : '我',
                    style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: Colors.white)),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(userName, style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w800, color: Colors.white)),
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
          child: Row(
            children: [
              _StatBox(value: '128', label: '已读', showBorder: true),
              _StatBox(value: '36', label: '收藏', showBorder: true),
              _StatBox(value: '9', label: '关注源'),
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
              _MenuItem(icon: Icons.format_list_bulleted, label: '我的订阅'),
              _MenuItem(icon: Icons.bookmark_outline, label: '收藏夹'),
              _MenuItem(icon: Icons.access_time, label: '阅读历史'),
              _MenuItem(icon: Icons.notifications_outlined, label: '推送与提醒设置'),
              _MenuItem(icon: Icons.info_outline, label: '数据源偏好'),
              _MenuItem(icon: Icons.logout, label: '退出登录', isLogout: true, onTap: onLogout),
            ],
          ),
        ),
      ],
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
