import 'package:flutter/material.dart';

import 'app_theme.dart';
import 'information_api.dart';

class ForgotPasswordPage extends StatefulWidget {
  const ForgotPasswordPage({super.key, required this.api, this.initialAccount = ''});
  final CampusApi api;
  final String initialAccount;

  @override
  State<ForgotPasswordPage> createState() => _ForgotPasswordPageState();
}

class _ForgotPasswordPageState extends State<ForgotPasswordPage> {
  late final TextEditingController _accountCtrl =
      TextEditingController(text: widget.initialAccount);
  final _tokenCtrl = TextEditingController();
  final _passwordCtrl = TextEditingController();
  final _confirmCtrl = TextEditingController();
  bool _loading = false;
  bool _tokenStep = false;
  bool _obscure = true;
  String? _hint;

  @override
  void dispose() {
    _accountCtrl.dispose();
    _tokenCtrl.dispose();
    _passwordCtrl.dispose();
    _confirmCtrl.dispose();
    super.dispose();
  }

  Future<void> _requestToken() async {
    final account = _accountCtrl.text.trim();
    if (account.isEmpty) {
      _toast('请输入用户名或邮箱');
      return;
    }
    setState(() => _loading = true);
    try {
      final developmentToken = await widget.api.forgotPassword(account);
      if (!mounted) return;
      setState(() {
        _tokenStep = true;
        if (developmentToken != null && developmentToken.isNotEmpty) {
          _tokenCtrl.text = developmentToken;
          _hint = '开发环境已自动填入一次性重置令牌。';
        } else {
          _hint = '若账号存在且已绑定邮箱，重置链接已发送。请查收邮件或粘贴令牌。';
        }
      });
    } catch (error) {
      _toast('发送失败：$error');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _reset() async {
    final token = _tokenCtrl.text.trim();
    final password = _passwordCtrl.text;
    if (token.isEmpty) {
      _toast('请输入重置令牌');
      return;
    }
    if (password.length < 6) {
      _toast('新密码至少 6 位');
      return;
    }
    if (password != _confirmCtrl.text) {
      _toast('两次输入的密码不一致');
      return;
    }
    setState(() => _loading = true);
    try {
      await widget.api.resetPassword(token, password);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('密码已重置，请使用新密码登录')),
      );
      Navigator.of(context).pop(true);
    } catch (error) {
      _toast('重置失败：$error');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _toast(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      appBar: AppBar(
        backgroundColor: AppTheme.bg,
        elevation: 0,
        foregroundColor: AppTheme.ink,
        title: const Text('找回密码'),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(22, 8, 22, 28),
        children: [
          Text(
            _tokenStep
                ? (_hint ?? '请设置新密码')
                : '输入用户名或绑定邮箱，获取一次性重置令牌。',
            style: const TextStyle(fontSize: 13, color: AppTheme.muted),
          ),
          const SizedBox(height: 18),
          if (!_tokenStep) ...[
            TextField(
              controller: _accountCtrl,
              decoration: _decoration('用户名或邮箱', Icons.person_outline),
            ),
            const SizedBox(height: 20),
            SizedBox(
              height: 50,
              child: FilledButton(
                onPressed: _loading ? null : _requestToken,
                style: FilledButton.styleFrom(backgroundColor: AppTheme.brand),
                child: _loading
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white),
                      )
                    : const Text('发送重置链接'),
              ),
            ),
          ] else ...[
            TextField(
              controller: _tokenCtrl,
              decoration: _decoration('重置令牌', Icons.vpn_key_outlined),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _passwordCtrl,
              obscureText: _obscure,
              decoration: _decoration('新密码', Icons.lock_outline).copyWith(
                suffixIcon: IconButton(
                  onPressed: () => setState(() => _obscure = !_obscure),
                  icon: Icon(
                    _obscure
                        ? Icons.visibility_outlined
                        : Icons.visibility_off_outlined,
                    color: AppTheme.muted,
                  ),
                ),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _confirmCtrl,
              obscureText: _obscure,
              decoration: _decoration('确认新密码', Icons.lock_outline),
            ),
            const SizedBox(height: 20),
            SizedBox(
              height: 50,
              child: FilledButton(
                onPressed: _loading ? null : _reset,
                style: FilledButton.styleFrom(backgroundColor: AppTheme.brand),
                child: _loading
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white),
                      )
                    : const Text('确认重置'),
              ),
            ),
            TextButton(
              onPressed: _loading
                  ? null
                  : () => setState(() {
                        _tokenStep = false;
                        _hint = null;
                      }),
              child: const Text('重新发送'),
            ),
          ],
        ],
      ),
    );
  }

  InputDecoration _decoration(String label, IconData icon) {
    return InputDecoration(
      labelText: label,
      prefixIcon: Icon(icon, color: AppTheme.muted),
      filled: true,
      fillColor: AppTheme.surface,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: const BorderSide(color: AppTheme.line),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: const BorderSide(color: AppTheme.line),
      ),
    );
  }
}
