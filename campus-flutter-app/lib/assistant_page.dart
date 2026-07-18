import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:url_launcher/url_launcher.dart';
import 'app_theme.dart';
import 'information_api.dart';

class PrototypeAssistantPage extends StatefulWidget {
  const PrototypeAssistantPage({super.key, required this.api, required this.session});
  final CampusApi api;
  final LoginSession session;

  @override
  State<PrototypeAssistantPage> createState() => _PrototypeAssistantPageState();
}

class _PrototypeAssistantPageState extends State<PrototypeAssistantPage> {
  final _ctrl = TextEditingController();
  final _messages = <_Msg>[];
  String? _sessionId;
  bool _loading = false;

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _send(String text) async {
    if (text.trim().isEmpty || _loading) return;
    setState(() {
      _messages.add(_Msg.me(text));
      _loading = true;
    });
    _ctrl.clear();
    try {
      final result = await widget.api.aiChat(_sessionId ?? '', text, widget.session);
      if (!mounted) return;
      setState(() {
        _sessionId = result.sessionId.isNotEmpty ? result.sessionId : _sessionId;
        _messages.add(_Msg.ai(_formatAnswer(result)));
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _messages.add(_Msg.ai('请求失败：$e'));
        _loading = false;
      });
    }
  }

  String _formatAnswer(AiChatResult result) {
    final buffer = StringBuffer(result.answer);
    buffer.write(result.grounded
        ? '\n\n> 已根据 ${result.sources.length} 条可核验来源回答 · ${result.retrievalMode}'
        : '\n\n> 当前回答没有可核验的数据依据 · ${result.retrievalMode}');
    if (result.sources.isNotEmpty) {
      buffer.write('\n\n**信息来源**\n');
      for (var i = 0; i < result.sources.length; i++) {
        final source = result.sources[i];
        final title = source.title.replaceAll('[', '［').replaceAll(']', '］');
        final uri = Uri.tryParse(source.originalUrl ?? '');
        final safeUrl = uri != null && (uri.scheme == 'https' || uri.scheme == 'http');
        buffer.write('\n${i + 1}. ${safeUrl ? '[$title]($uri)' : title}');
        if (source.sourceName?.isNotEmpty == true) {
          buffer.write(' — ${source.sourceName}');
        }
      }
    }
    return buffer.toString();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(20, 6, 20, 16),
            children: [
              // AI Top banner
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(AppTheme.radius),
                  gradient: AppTheme.brandDarkGradient,
                ),
                child: Row(
                  children: [
                    Container(
                      width: 40, height: 40,
                      decoration: BoxDecoration(
                        color: Colors.white.withValues(alpha: 0.18),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Icon(Icons.auto_awesome, color: Colors.white, size: 22),
                    ),
                    const SizedBox(width: 12),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('AI 校园助手', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: Colors.white)),
                          SizedBox(height: 2),
                          Text('基于多源信息的智能化 · 实时在线', style: TextStyle(fontSize: 11.5, color: Colors.white70)),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              // Welcome
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(color: AppTheme.surface, borderRadius: BorderRadius.circular(AppTheme.radiusSm), border: Border.all(color: AppTheme.line)),
                child: Text.rich(
                  TextSpan(
                    style: const TextStyle(fontSize: 13, color: AppTheme.ink2, height: 1.55),
                    children: [
                      TextSpan(text: '你好，${widget.session.user.username}。我已读取你关注的 '),
                      const TextSpan(text: '教务、课程、活动', style: TextStyle(fontWeight: FontWeight.w700, color: AppTheme.ink)),
                      const TextSpan(text: ' 等 6 类信息源。可以问我「今天有什么重要通知」「帮我找最近的讲座」或「我的课表有冲突吗」。'),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 14),
              // Suggestions
              ...[
                ('今天有什么重要通知？', Icons.add),
                ('帮我找最近的 AI 讲座', Icons.search),
                ('我的课表有冲突吗？', Icons.format_list_bulleted),
              ].map((e) => Padding(
                padding: const EdgeInsets.only(bottom: 9),
                child: GestureDetector(
                  onTap: () => _send(e.$1),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 11),
                    decoration: BoxDecoration(color: AppTheme.brandSoft, borderRadius: BorderRadius.circular(12)),
                    child: Row(
                      children: [
                        Icon(e.$2, color: AppTheme.brandInk, size: 15),
                        const SizedBox(width: 9),
                        Text(e.$1, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppTheme.brandInk)),
                      ],
                    ),
                  ),
                ),
              )),
              const SizedBox(height: 8),
              // Messages
              for (final msg in _messages) ...[
                _Bubble(msg: msg),
                const SizedBox(height: 12),
              ],
              if (_loading) ...[
                const _LoadingBubble(),
                const SizedBox(height: 12),
              ],
            ],
          ),
        ),
        // Input bar
        Container(
          padding: const EdgeInsets.fromLTRB(16, 8, 8, 8),
          decoration: BoxDecoration(color: AppTheme.surface, border: Border(top: BorderSide(color: AppTheme.line))),
          child: Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _ctrl,
                  textInputAction: TextInputAction.send,
                  onSubmitted: _send,
                  style: const TextStyle(fontSize: 13.5, color: AppTheme.ink),
                  decoration: const InputDecoration(
                    border: InputBorder.none,
                    hintText: '问我任何校园问题…',
                    hintStyle: TextStyle(fontSize: 13.5, color: AppTheme.muted),
                    isDense: true,
                    contentPadding: EdgeInsets.symmetric(vertical: 10),
                  ),
                ),
              ),
              GestureDetector(
                onTap: () => _send(_ctrl.text),
                child: Container(
                  width: 38, height: 38,
                  decoration: const BoxDecoration(color: AppTheme.brand, shape: BoxShape.circle),
                  child: const Icon(Icons.send, color: Colors.white, size: 18),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _LoadingBubble extends StatelessWidget {
  const _LoadingBubble();
  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Container(
          width: 30, height: 30,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            gradient: AppTheme.brandGradient,
          ),
          child: const Icon(Icons.auto_awesome, color: Colors.white, size: 16),
        ),
        const SizedBox(width: 9),
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: AppTheme.surface,
            borderRadius: BorderRadius.only(
              topLeft: const Radius.circular(14),
              topRight: const Radius.circular(14),
              bottomLeft: Radius.circular(4),
              bottomRight: const Radius.circular(14),
            ),
            border: Border.all(color: AppTheme.line),
          ),
          child: const SizedBox(
            width: 60, height: 16,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                _Dot(delay: 0),
                _Dot(delay: 150),
                _Dot(delay: 300),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _Dot extends StatefulWidget {
  const _Dot({required this.delay});
  final int delay;
  @override
  State<_Dot> createState() => _DotState();
}

class _DotState extends State<_Dot> with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;
  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(vsync: this, duration: const Duration(milliseconds: 600))
      ..repeat(reverse: true);
  }
  @override
  void dispose() { _ctrl.dispose(); super.dispose(); }
  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _ctrl,
      builder: (_, __) => Container(
        width: 8, height: 8,
        decoration: BoxDecoration(
          color: AppTheme.muted.withValues(alpha: 0.3 + _ctrl.value * 0.5),
          shape: BoxShape.circle,
        ),
      ),
    );
  }
}
class _Msg {
  final String text;
  final bool isMe;
  _Msg.ai(this.text) : isMe = false;
  _Msg.me(this.text) : isMe = true;
}

class _Bubble extends StatelessWidget {
  const _Bubble({required this.msg});
  final _Msg msg;

  @override
  Widget build(BuildContext context) {
    if (msg.isMe) {
      return Row(
        mainAxisAlignment: MainAxisAlignment.end,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Flexible(
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppTheme.brand,
                borderRadius: BorderRadius.only(
                  topLeft: const Radius.circular(14),
                  topRight: const Radius.circular(14),
                  bottomLeft: const Radius.circular(14),
                  bottomRight: Radius.circular(4),
                ),
              ),
              child: Text(msg.text, style: const TextStyle(fontSize: 13, color: Colors.white, height: 1.55)),
            ),
          ),
        ],
      );
    }
    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Container(
          width: 30, height: 30,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            gradient: AppTheme.brandGradient,
          ),
          child: const Icon(Icons.auto_awesome, color: Colors.white, size: 16),
        ),
        const SizedBox(width: 9),
        Flexible(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppTheme.surface,
                  borderRadius: BorderRadius.only(
                    topLeft: const Radius.circular(14),
                    topRight: const Radius.circular(14),
                    bottomLeft: Radius.circular(4),
                    bottomRight: const Radius.circular(14),
                  ),
                  border: Border.all(color: AppTheme.line),
                ),
                child: MarkdownBody(
                  data: msg.text,
                  selectable: true,
                  onTapLink: (_, href, __) {
                    final uri = Uri.tryParse(href ?? '');
                    if (uri != null && (uri.scheme == 'https' || uri.scheme == 'http')) {
                      launchUrl(uri, mode: LaunchMode.externalApplication);
                    }
                  },
                  styleSheet: MarkdownStyleSheet(
                    p: const TextStyle(fontSize: 13, color: AppTheme.ink2, height: 1.55),
                    h3: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: AppTheme.ink, height: 1.6),
                    strong: const TextStyle(fontWeight: FontWeight.w700, color: AppTheme.ink),
                    code: TextStyle(fontSize: 11.5, color: AppTheme.brandInk, backgroundColor: AppTheme.brandSoft, fontFamily: 'monospace'),
                    codeblockDecoration: BoxDecoration(color: AppTheme.surface2, borderRadius: BorderRadius.circular(8)),
                    blockquoteDecoration: BoxDecoration(
                      border: Border(left: BorderSide(color: AppTheme.brand, width: 3)),
                    ),
                    blockquotePadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    listBullet: const TextStyle(fontSize: 13, color: AppTheme.ink2),
                  ),
                ),
              ),
              const SizedBox(height: 6),
              // Citation
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                decoration: BoxDecoration(color: AppTheme.surface2, borderRadius: BorderRadius.circular(10)),
                child: const Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.auto_awesome, size: 12, color: AppTheme.brandInk),
                    SizedBox(width: 6),
                    Text('CampusMind AI', style: TextStyle(fontSize: 11.5, color: AppTheme.ink2)),
                  ],
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
