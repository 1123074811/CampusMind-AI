import 'package:flutter/material.dart';
import 'app_theme.dart';
import 'information_api.dart';

/// 导入结果预览确认页
///
/// 展示 AI 解析出的字段（标题、时间、地点、标签、摘要），
/// 用户确认后入库，失败可重试。
class ImportPreviewPage extends StatelessWidget {
  const ImportPreviewPage({
    super.key,
    required this.result,
    required this.api,
    required this.session,
    this.onConfirmed,
    this.onRetry,
  });

  final ImportResult result;
  final CampusApi api;
  final LoginSession session;

  /// 用户确认入库后回调（createdItemId 存在时已自动入库）。
  final ValueChanged<int?>? onConfirmed;

  /// 用户点击重试时回调。
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final isSuccess = result.status == 'SUCCESS';
    final hasParsed = result.hasParsedData;

    return Scaffold(
      backgroundColor: AppTheme.bg,
      appBar: AppBar(
        backgroundColor: AppTheme.bg,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new,
              size: 20, color: AppTheme.ink),
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: const Text('导入预览',
            style: TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w700,
                color: AppTheme.ink)),
        centerTitle: true,
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 20),
              children: [
                _StatusBanner(
                  isSuccess: isSuccess,
                  message: result.message,
                ),
                const SizedBox(height: 16),
                if (hasParsed)
                  _ParsedPreviewCard(result: result)
                else
                  _NoParsedHint(isSuccess: isSuccess),
                const SizedBox(height: 16),
                _TaskInfoRow(result: result),
              ],
            ),
          ),
          _BottomActionBar(
            isSuccess: isSuccess,
            createdItemId: result.createdItemId,
            onConfirm: () {
              onConfirmed?.call(result.createdItemId);
              Navigator.of(context).pop(result.createdItemId);
            },
            onRetry: onRetry != null
                ? () {
                    onRetry!();
                    Navigator.of(context).pop();
                  }
                : null,
          ),
        ],
      ),
    );
  }
}

// ─── 子组件 ─────────────────────────────────

class _StatusBanner extends StatelessWidget {
  const _StatusBanner({required this.isSuccess, required this.message});
  final bool isSuccess;
  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: isSuccess ? AppTheme.brandSoft : AppTheme.accentSoft,
        borderRadius: BorderRadius.circular(AppTheme.radiusSm),
      ),
      child: Row(
        children: [
          Icon(
            isSuccess ? Icons.check_circle : Icons.pending,
            size: 22,
            color: isSuccess ? AppTheme.brandInk : AppTheme.accent,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              message.isNotEmpty ? message : (isSuccess ? '导入成功' : '处理中'),
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w700,
                color: isSuccess ? AppTheme.brandInk : const Color(0xFFB45309),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ParsedPreviewCard extends StatelessWidget {
  const _ParsedPreviewCard({required this.result});
  final ImportResult result;

  static String _eventTypeLabel(String t) {
    return switch (t.toUpperCase()) {
      'NOTICE' => '通知',
      'COURSE' => '课程',
      'EXAM' => '考试',
      'HOMEWORK' => '作业',
      'ACTIVITY' => '活动',
      'LECTURE' => '讲座',
      'COMPETITION' => '竞赛',
      'SERVICE' => '服务',
      _ => t,
    };
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(AppTheme.radiusSm),
        border: Border.all(color: AppTheme.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 头部标签
          Row(
            children: [
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                    color: AppTheme.brandSoft,
                    borderRadius: BorderRadius.circular(6)),
                child: const Text('AI 解析',
                    style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: AppTheme.brandInk)),
              ),
              const SizedBox(width: 8),
              if (result.parsedEventType != null)
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                      color: AppTheme.surface2,
                      borderRadius: BorderRadius.circular(6)),
                  child: Text(_eventTypeLabel(result.parsedEventType!),
                      style: const TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                          color: AppTheme.ink2)),
                ),
            ],
          ),
          const SizedBox(height: 12),

          // 标题
          Text(
            result.parsedTitle ?? '未识别标题',
            style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w800,
                color: AppTheme.ink,
                height: 1.4),
          ),
          const SizedBox(height: 12),

          // 时间
          if (result.parsedTime != null && result.parsedTime!.isNotEmpty)
            _FieldRow(
                icon: Icons.access_time,
                label: '时间',
                value: result.parsedTime!),

          // 地点
          if (result.parsedLocation != null &&
              result.parsedLocation!.isNotEmpty)
            _FieldRow(
                icon: Icons.location_on_outlined,
                label: '地点',
                value: result.parsedLocation!),

          // 摘要
          if (result.parsedSummary != null &&
              result.parsedSummary!.isNotEmpty) ...[
            const SizedBox(height: 6),
            const Text('摘要',
                style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: AppTheme.muted)),
            const SizedBox(height: 4),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: AppTheme.surface2,
                borderRadius: BorderRadius.circular(9),
              ),
              child: Text(
                result.parsedSummary!,
                style: const TextStyle(
                    fontSize: 13, color: AppTheme.ink2, height: 1.6),
              ),
            ),
          ],

          // 标签
          if (result.parsedTags.isNotEmpty) ...[
            const SizedBox(height: 10),
            Wrap(
              spacing: 6,
              runSpacing: 5,
              children: result.parsedTags
                  .map((tag) => Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 9, vertical: 4),
                        decoration: BoxDecoration(
                          color: AppTheme.brandSoft,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Text('#$tag',
                            style: const TextStyle(
                                fontSize: 11.5,
                                color: AppTheme.brandInk,
                                fontWeight: FontWeight.w500)),
                      ))
                  .toList(),
            ),
          ],
        ],
      ),
    );
  }
}

class _FieldRow extends StatelessWidget {
  const _FieldRow(
      {required this.icon, required this.label, required this.value});
  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 5),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 14, color: AppTheme.muted),
          const SizedBox(width: 6),
          Text('$label：',
              style: const TextStyle(
                  fontSize: 12.5,
                  fontWeight: FontWeight.w600,
                  color: AppTheme.muted)),
          Expanded(
            child: Text(value,
                style: const TextStyle(
                    fontSize: 12.5,
                    color: AppTheme.ink,
                    fontWeight: FontWeight.w600)),
          ),
        ],
      ),
    );
  }
}

class _NoParsedHint extends StatelessWidget {
  const _NoParsedHint({required this.isSuccess});
  final bool isSuccess;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(AppTheme.radiusSm),
        border: Border.all(color: AppTheme.line),
      ),
      child: Column(
        children: [
          Icon(
            isSuccess ? Icons.check_circle_outline : Icons.hourglass_top,
            size: 40,
            color: AppTheme.muted,
          ),
          const SizedBox(height: 10),
          Text(
            isSuccess
                ? '内容已成功导入，可在「我的导入」中查看'
                : '任务正在后台处理中，稍后可在「我的导入」中查看结果',
            textAlign: TextAlign.center,
            style: const TextStyle(
                fontSize: 13, color: AppTheme.ink2, height: 1.5),
          ),
        ],
      ),
    );
  }
}

class _TaskInfoRow extends StatelessWidget {
  const _TaskInfoRow({required this.result});
  final ImportResult result;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: AppTheme.surface2,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        children: [
          Text('任务 #${result.taskId}',
              style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: AppTheme.muted)),
          const Spacer(),
          if (result.createdItemId != null)
            Text('已创建条目 #${result.createdItemId}',
                style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: AppTheme.brandInk)),
        ],
      ),
    );
  }
}

class _BottomActionBar extends StatelessWidget {
  const _BottomActionBar({
    required this.isSuccess,
    required this.createdItemId,
    required this.onConfirm,
    this.onRetry,
  });
  final bool isSuccess;
  final int? createdItemId;
  final VoidCallback onConfirm;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 10, 20, 20),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        border: Border(top: BorderSide(color: AppTheme.line)),
      ),
      child: Row(
        children: [
          if (!isSuccess && onRetry != null)
            Expanded(
              child: OutlinedButton(
                onPressed: onRetry,
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppTheme.rose,
                  side: const BorderSide(color: AppTheme.rose),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14)),
                  padding: const EdgeInsets.symmetric(vertical: 14),
                ),
                child: const Text('重新导入',
                    style: TextStyle(
                        fontSize: 14, fontWeight: FontWeight.w700)),
              ),
            ),
          if (!isSuccess && onRetry != null) const SizedBox(width: 12),
          Expanded(
            flex: (!isSuccess && onRetry != null) ? 1 : 2,
            child: SizedBox(
              height: 50,
              child: ElevatedButton(
                onPressed: onConfirm,
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTheme.brand,
                  foregroundColor: Colors.white,
                  elevation: 0,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                ),
                child: Text(
                  isSuccess
                      ? (createdItemId != null ? '查看结果' : '确认')
                      : '返回',
                  style: const TextStyle(
                      fontSize: 15, fontWeight: FontWeight.w700),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
