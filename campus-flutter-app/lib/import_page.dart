import 'dart:io';

import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';

import 'app_theme.dart';
import 'information_api.dart';
import 'import_preview_page.dart';
import 'my_imported_events_page.dart';

class ImportPage extends StatefulWidget {
  const ImportPage({super.key, required this.api, required this.session});
  final CampusApi api;
  final LoginSession session;

  @override
  State<ImportPage> createState() => _ImportPageState();
}

class _ImportPageState extends State<ImportPage>
    with SingleTickerProviderStateMixin {
  late final TabController _tabCtrl;
  final _tabs = const ['粘贴文本', '文件', '雨课堂'];

  // 文本
  final _textCtrl = TextEditingController();

  // 文件
  String? _pickedFilePath;

  // 雨课堂
  final _rainJsonCtrl = TextEditingController();
  String _rainDataType = 'HOMEWORK';

  // 状态
  bool _submitting = false;
  ImportResult? _lastResult;

  @override
  void initState() {
    super.initState();
    _tabCtrl = TabController(length: _tabs.length, vsync: this);
  }

  @override
  void dispose() {
    _tabCtrl.dispose();
    _textCtrl.dispose();
    _rainJsonCtrl.dispose();
    super.dispose();
  }

  // ─── 提交逻辑 ─────────────────────────────────

  Future<void> _submit() async {
    if (_submitting) return;
    setState(() {
      _submitting = true;
      _lastResult = null;
    });

    try {
      final result = switch (_tabCtrl.index) {
        0 => await _submitText(),
        1 => await _submitFile(),
        2 => await _submitRainJson(),
        _ => throw Exception('未知导入类型'),
      };
      if (!mounted) return;
      setState(() => _lastResult = result);
      _openPreview(result);
    } catch (e) {
      if (!mounted) return;
      if (e is SessionExpiredException) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e.message), backgroundColor: AppTheme.rose),
        );
        Navigator.of(context).pop(); // 返回主页面，用户需重新登录
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('导入失败：$e'), backgroundColor: AppTheme.rose),
      );
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<ImportResult> _submitText() async {
    final text = _textCtrl.text.trim();
    if (text.isEmpty) throw Exception('请输入文本内容');
    return widget.api.importText(text, widget.session);
  }

  Future<ImportResult> _submitFile() async {
    if (_pickedFilePath == null) throw Exception('请先选择文件');
    final bytes = await File(_pickedFilePath!).readAsBytes();
    final name = _pickedFilePath!.split(Platform.pathSeparator).last;
    return widget.api.importFile(bytes, name, widget.session);
  }

  Future<ImportResult> _submitRainJson() async {
    final json = _rainJsonCtrl.text.trim();
    if (json.isEmpty) throw Exception('请粘贴雨课堂JSON数据');
    return widget.api.importRainJson(_rainDataType, json, widget.session);
  }

  void _openPreview(ImportResult result) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ImportPreviewPage(
          result: result,
          api: widget.api,
          session: widget.session,
          onConfirmed: (itemId) {
            // 导入成功并确认后，返回时清空表单
            if (mounted) {
              _textCtrl.clear();
              _rainJsonCtrl.clear();
              setState(() {
                _pickedFilePath = null;
                _lastResult = null;
              });
            }
          },
          onRetry: () => _submit(),
        ),
      ),
    );
  }

  // ─── 选择操作 ─────────────────────────────────

  Future<void> _pickFile() async {
    try {
      const documentTypes = XTypeGroup(
        label: '支持的文档',
        extensions: ['pdf', 'docx', 'txt', 'xlsx'],
      );
      final file = await openFile(acceptedTypeGroups: [documentTypes]);
      if (file != null) {
        if (!mounted) return;
        setState(() => _pickedFilePath = file.path);
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('选择文件失败：$e'), backgroundColor: AppTheme.rose),
      );
    }
  }

  // ─── 构建 ─────────────────────────────────

  @override
  Widget build(BuildContext context) {
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
        title: const Text('导入信息',
            style: TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w700,
                color: AppTheme.ink)),
        centerTitle: true,
      ),
      body: Column(
        children: [
          // Tab bar
          Container(
            margin: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
            padding: const EdgeInsets.all(3),
            decoration: BoxDecoration(
              color: AppTheme.surface,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: AppTheme.line),
            ),
            child: TabBar(
              controller: _tabCtrl,
              indicator: BoxDecoration(
                color: AppTheme.brand,
                borderRadius: BorderRadius.circular(11),
              ),
              indicatorSize: TabBarIndicatorSize.tab,
              dividerColor: Colors.transparent,
              labelColor: Colors.white,
              unselectedLabelColor: AppTheme.ink2,
              labelStyle:
                  const TextStyle(fontSize: 13, fontWeight: FontWeight.w700),
              unselectedLabelStyle:
                  const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
              tabs: _tabs.map((t) => Tab(text: t)).toList(),
            ),
          ),
          // Content
          Expanded(
            child: TabBarView(
              controller: _tabCtrl,
              children: [
                _buildTextTab(),
                _buildFileTab(),
                _buildRainTab(),
              ],
            ),
          ),
          // Result + Submit
          _buildBottomBar(),
        ],
      ),
    );
  }

  // ─── 文本 Tab ─────────────────────────────────

  Widget _buildTextTab() {
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
      children: [
        const _SectionHint(
          icon: Icons.notes,
          title: '粘贴通知文本',
          desc: '将通知、公告、课程信息等文本粘贴到下方，AI 将自动分析并生成结构化事件',
        ),
        const SizedBox(height: 14),
        Container(
          decoration: BoxDecoration(
            color: AppTheme.surface,
            borderRadius: BorderRadius.circular(AppTheme.radiusSm),
            border: Border.all(color: AppTheme.line),
          ),
          child: Column(
            children: [
              TextField(
                controller: _textCtrl,
                maxLines: 14,
                maxLength: 20000,
                style: const TextStyle(
                    fontSize: 14, color: AppTheme.ink, height: 1.6),
                decoration: const InputDecoration(
                  border: InputBorder.none,
                  hintText: '在此粘贴通知文本内容…\n\n支持粘贴教务通知、学院公告、课程安排、考试通知、活动信息等。',
                  hintStyle: TextStyle(fontSize: 14, color: AppTheme.muted),
                  contentPadding: EdgeInsets.all(16),
                  counterText: '',
                ),
              ),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                decoration: const BoxDecoration(
                  border: Border(top: BorderSide(color: AppTheme.line)),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      '${_textCtrl.text.length} / 20000',
                      style:
                          const TextStyle(fontSize: 12, color: AppTheme.muted),
                    ),
                    GestureDetector(
                      onTap: () => _textCtrl.clear(),
                      child: const Text('清空',
                          style: TextStyle(
                              fontSize: 12,
                              color: AppTheme.rose,
                              fontWeight: FontWeight.w600)),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  // ─── 文件 Tab ─────────────────────────────────

  Widget _buildFileTab() {
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
      children: [
        const _SectionHint(
          icon: Icons.upload_file,
          title: '上传文档文件',
          desc: '支持 PDF、DOCX、TXT、XLSX 格式，系统将自动提取文本并分析',
        ),
        const SizedBox(height: 14),
        _PickButton(
          icon: Icons.folder_open,
          label: '选择文件（最大 10MB）',
          onTap: _pickFile,
        ),
        const SizedBox(height: 16),
        if (_pickedFilePath != null)
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: AppTheme.surface,
              borderRadius: BorderRadius.circular(AppTheme.radiusSm),
              border: Border.all(color: AppTheme.line),
            ),
            child: Row(
              children: [
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: AppTheme.brandSoft,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(_fileIcon(_pickedFilePath!.split('.').last),
                      color: AppTheme.brandInk, size: 22),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(_pickedFilePath!.split(Platform.pathSeparator).last,
                          style: const TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w600,
                              color: AppTheme.ink),
                          overflow: TextOverflow.ellipsis),
                      const SizedBox(height: 2),
                      FutureBuilder<FileStat>(
                        future: FileStat.stat(_pickedFilePath!),
                        builder: (ctx, snap) {
                          if (!snap.hasData) return const SizedBox.shrink();
                          return Text(
                            '${(snap.data!.size / 1024).toStringAsFixed(1)} KB',
                            style: const TextStyle(
                                fontSize: 12, color: AppTheme.muted),
                          );
                        },
                      ),
                    ],
                  ),
                ),
                GestureDetector(
                  onTap: () => setState(() => _pickedFilePath = null),
                  child:
                      const Icon(Icons.close, color: AppTheme.muted, size: 20),
                ),
              ],
            ),
          )
        else
          _EmptyPlaceholder(
            icon: Icons.description_outlined,
            text: '尚未选择文件',
          ),
        const SizedBox(height: 16),
        // 格式说明
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: AppTheme.infoSoft,
            borderRadius: BorderRadius.circular(10),
          ),
          child: const Row(
            children: [
              Icon(Icons.info_outline, color: AppTheme.info, size: 18),
              SizedBox(width: 8),
              Expanded(
                child: Text(
                  '支持格式：PDF (.pdf)、Word (.docx)、文本 (.txt)、Excel (.xlsx)',
                  style: TextStyle(
                      fontSize: 12, color: AppTheme.info, height: 1.5),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  // ─── 雨课堂 Tab ─────────────────────────────────

  Widget _buildRainTab() {
    return _buildRainJson();
  }

  Widget _buildRainJson() {
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
      children: [
        const _SectionHint(
          icon: Icons.data_object,
          title: '粘贴雨课堂 JSON',
          desc: '从浏览器开发者工具复制课程/作业/通知的响应 JSON 数据',
        ),
        const SizedBox(height: 14),
        // 数据类型选择
        Wrap(
          spacing: 8,
          children: ['HOMEWORK', 'COURSE', 'NOTICE', 'EXAM'].map((t) {
            final active = _rainDataType == t;
            return GestureDetector(
              onTap: () => setState(() => _rainDataType = t),
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: active ? AppTheme.brand : AppTheme.surface,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                      color: active ? AppTheme.brand : AppTheme.line),
                ),
                child: Text(
                  _rainTypeLabel(t),
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: active ? Colors.white : AppTheme.ink2,
                  ),
                ),
              ),
            );
          }).toList(),
        ),
        const SizedBox(height: 12),
        Container(
          decoration: BoxDecoration(
            color: AppTheme.surface,
            borderRadius: BorderRadius.circular(AppTheme.radiusSm),
            border: Border.all(color: AppTheme.line),
          ),
          child: TextField(
            controller: _rainJsonCtrl,
            maxLines: 12,
            style: const TextStyle(
                fontSize: 13,
                color: AppTheme.ink,
                fontFamily: 'monospace',
                height: 1.5),
            decoration: const InputDecoration(
              border: InputBorder.none,
              hintText: '{"data":{"list":[...]}}',
              hintStyle: TextStyle(fontSize: 13, color: AppTheme.muted),
              contentPadding: EdgeInsets.all(14),
            ),
          ),
        ),
      ],
    );
  }

  // ─── 底部操作栏 ─────────────────────────────────

  Widget _buildBottomBar() {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 10, 20, 20),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        border: Border(top: BorderSide(color: AppTheme.line)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // 最近结果
          if (_lastResult != null)
            Container(
              width: double.infinity,
              margin: const EdgeInsets.only(bottom: 10),
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: _lastResult!.status == 'SUCCESS'
                    ? AppTheme.brandSoft
                    : AppTheme.accentSoft,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Row(
                children: [
                  Icon(
                    _lastResult!.status == 'SUCCESS'
                        ? Icons.check_circle
                        : Icons.pending,
                    size: 18,
                    color: _lastResult!.status == 'SUCCESS'
                        ? AppTheme.brandInk
                        : AppTheme.accent,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      _lastResult!.message,
                      style: TextStyle(
                        fontSize: 13,
                        color: _lastResult!.status == 'SUCCESS'
                            ? AppTheme.brandInk
                            : const Color(0xFFB45309),
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  if (_tabCtrl.index == 2 && _lastResult!.status == 'SUCCESS')
                    TextButton(
                      onPressed: () =>
                          Navigator.of(context).push(MaterialPageRoute(
                        builder: (_) => MyImportedEventsPage(
                            api: widget.api, session: widget.session),
                      )),
                      child: const Text('查看'),
                    ),
                ],
              ),
            ),
          // 提交按钮
          SizedBox(
            width: double.infinity,
            height: 50,
            child: ElevatedButton(
              onPressed: _submitting ? null : _submit,
              style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.brand,
                foregroundColor: Colors.white,
                disabledBackgroundColor: AppTheme.brand.withValues(alpha: 0.5),
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
              ),
              child: _submitting
                  ? const SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(
                          strokeWidth: 2.5, color: Colors.white))
                  : const Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.auto_awesome, size: 18),
                        SizedBox(width: 8),
                        Text('提交 AI 分析',
                            style: TextStyle(
                                fontSize: 15, fontWeight: FontWeight.w700)),
                      ],
                    ),
            ),
          ),
        ],
      ),
    );
  }

  // ─── 辅助方法 ─────────────────────────────────

  IconData _fileIcon(String? ext) {
    return switch (ext?.toLowerCase()) {
      'pdf' => Icons.picture_as_pdf,
      'docx' => Icons.article,
      'xlsx' => Icons.table_chart,
      'txt' => Icons.text_snippet,
      _ => Icons.insert_drive_file,
    };
  }

  String _rainTypeLabel(String t) {
    return switch (t) {
      'HOMEWORK' => '作业',
      'COURSE' => '课程',
      'NOTICE' => '通知',
      'EXAM' => '考试',
      _ => t,
    };
  }
}

// ─── 子组件 ─────────────────────────────────

class _SectionHint extends StatelessWidget {
  const _SectionHint(
      {required this.icon, required this.title, required this.desc});
  final IconData icon;
  final String title;
  final String desc;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: AppTheme.brandSoft,
            borderRadius: BorderRadius.circular(10),
          ),
          child: Icon(icon, color: AppTheme.brandInk, size: 18),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title,
                  style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                      color: AppTheme.ink)),
              const SizedBox(height: 2),
              Text(desc,
                  style: const TextStyle(
                      fontSize: 12.5, color: AppTheme.muted, height: 1.5)),
            ],
          ),
        ),
      ],
    );
  }
}

class _PickButton extends StatelessWidget {
  const _PickButton(
      {required this.icon, required this.label, required this.onTap});
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 16),
        decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(AppTheme.radiusSm),
          border: Border.all(color: AppTheme.line),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, color: AppTheme.brandInk, size: 20),
            const SizedBox(width: 8),
            Text(label,
                style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: AppTheme.brandInk)),
          ],
        ),
      ),
    );
  }
}

class _EmptyPlaceholder extends StatelessWidget {
  const _EmptyPlaceholder({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(40),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(AppTheme.radiusSm),
        border: Border.all(color: AppTheme.line),
      ),
      child: Column(
        children: [
          Icon(icon, size: 44, color: AppTheme.muted),
          const SizedBox(height: 10),
          Text(text,
              style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: AppTheme.muted)),
        ],
      ),
    );
  }
}
