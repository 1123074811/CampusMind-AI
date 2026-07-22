import 'package:flutter/material.dart';

import 'app_theme.dart';
import 'rain_sync_manager.dart';

class RainSyncProgressPage extends StatelessWidget {
  const RainSyncProgressPage({super.key});

  @override
  Widget build(BuildContext context) {
    final manager = RainSyncManager.instance;
    return Scaffold(
      backgroundColor: AppTheme.bg,
      appBar: AppBar(
        title: const Text('雨课堂同步'),
        centerTitle: true,
      ),
      body: AnimatedBuilder(
        animation: manager,
        builder: (context, _) => Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(_status(manager),
                  style: const TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.w700,
                      color: AppTheme.ink)),
              const SizedBox(height: 16),
              LinearProgressIndicator(
                value: manager.progress,
                minHeight: 8,
                borderRadius: BorderRadius.circular(8),
              ),
              const SizedBox(height: 12),
              Text(
                manager.phase == RainSyncPhase.importing
                    ? '信息 ${manager.processedItems}/${manager.totalItems}'
                    : manager.totalCourses == 0
                        ? '正在读取课程列表'
                        : '课程 ${manager.syncedCourses}/${manager.totalCourses}',
                style: const TextStyle(color: AppTheme.ink2),
              ),
              const SizedBox(height: 24),
              Row(
                children: [
                  _Stat(label: '采集', value: manager.collected),
                  _Stat(label: '写入', value: manager.success),
                  _Stat(label: '重复', value: manager.skipped),
                  _Stat(label: '失败', value: manager.failed),
                ],
              ),
              if (manager.error != null) ...[
                const SizedBox(height: 20),
                Text(manager.error!,
                    style: const TextStyle(color: AppTheme.rose)),
              ],
              const Spacer(),
              Text(
                manager.active ? '同步会继续在后台运行' : '同步任务已结束',
                textAlign: TextAlign.center,
                style: const TextStyle(color: AppTheme.muted),
              ),
              const SizedBox(height: 12),
              FilledButton(
                onPressed: () => Navigator.of(context).pop(),
                child: Text(manager.active ? '关闭并后台运行' : '完成'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _status(RainSyncManager manager) => switch (manager.phase) {
        RainSyncPhase.collecting => '正在同步课程与消息',
        RainSyncPhase.importing => '正在写入 CampusMind',
        RainSyncPhase.success => '同步完成',
        RainSyncPhase.failed => '同步失败',
        RainSyncPhase.idle => '等待登录',
      };
}

class _Stat extends StatelessWidget {
  const _Stat({required this.label, required this.value});

  final String label;
  final int value;

  @override
  Widget build(BuildContext context) => Expanded(
        child: Column(
          children: [
            Text('$value',
                style: const TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w700,
                    color: AppTheme.brandInk)),
            const SizedBox(height: 4),
            Text(label, style: const TextStyle(color: AppTheme.muted)),
          ],
        ),
      );
}
