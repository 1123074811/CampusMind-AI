import 'package:flutter/material.dart';

import 'app_theme.dart';
import 'information_api.dart';

class RegisterPage extends StatefulWidget {
  const RegisterPage({super.key, required this.api});
  final CampusApi api;

  @override
  State<RegisterPage> createState() => _RegisterPageState();
}

class _RegisterPageState extends State<RegisterPage> {
  final _usernameCtrl = TextEditingController();
  final _emailCtrl = TextEditingController();
  final _passwordCtrl = TextEditingController();
  final _confirmCtrl = TextEditingController();
  bool _obscure = true;
  bool _loading = false;
  bool _agreed = true;

  @override
  void dispose() {
    _usernameCtrl.dispose();
    _emailCtrl.dispose();
    _passwordCtrl.dispose();
    _confirmCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_loading) return;
    final username = _usernameCtrl.text.trim();
    final email = _emailCtrl.text.trim();
    final password = _passwordCtrl.text;
    final confirm = _confirmCtrl.text;
    if (username.length < 3) {
      _toast('用户名至少 3 位');
      return;
    }
    if (!email.contains('@') || email.length < 5) {
      _toast('请输入有效邮箱');
      return;
    }
    if (password.length < 6) {
      _toast('密码至少 6 位');
      return;
    }
    if (password != confirm) {
      _toast('两次输入的密码不一致');
      return;
    }
    if (!_agreed) {
      _toast('请先同意用户协议与隐私政策');
      return;
    }
    setState(() => _loading = true);
    try {
      final session =
          await widget.api.register(username, email, password);
      try {
        await widget.api
            .updateConsent('PRIVACY_POLICY', true, '2026-07-14', session);
        await widget.api
            .updateConsent('PERSONALIZATION', true, '2026-07-14', session);
      } catch (_) {
        // 授权失败不阻断注册成功后的登录，后续可在个人中心补授权。
      }
      if (!mounted) return;
      Navigator.of(context).pop(session);
    } catch (error) {
      _toast('注册失败：$error');
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
        title: const Text('注册学生账号'),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(22, 8, 22, 28),
        children: [
          const Text(
            '创建账号后可同步订阅、提醒与隐私授权。',
            style: TextStyle(fontSize: 13, color: AppTheme.muted),
          ),
          const SizedBox(height: 18),
          _field(_usernameCtrl, '用户名', Icons.person_outline),
          const SizedBox(height: 12),
          _field(_emailCtrl, '邮箱', Icons.mail_outline,
              keyboard: TextInputType.emailAddress),
          const SizedBox(height: 12),
          _field(_passwordCtrl, '密码（至少 6 位）', Icons.lock_outline,
              obscure: _obscure,
              suffix: IconButton(
                onPressed: () => setState(() => _obscure = !_obscure),
                icon: Icon(
                  _obscure
                      ? Icons.visibility_outlined
                      : Icons.visibility_off_outlined,
                  color: AppTheme.muted,
                ),
              )),
          const SizedBox(height: 12),
          _field(_confirmCtrl, '确认密码', Icons.lock_outline, obscure: _obscure),
          const SizedBox(height: 14),
          GestureDetector(
            onTap: () => setState(() => _agreed = !_agreed),
            child: Row(
              children: [
                Icon(
                  _agreed ? Icons.check_box : Icons.check_box_outline_blank,
                  color: _agreed ? AppTheme.brand : AppTheme.muted,
                  size: 20,
                ),
                const SizedBox(width: 8),
                const Expanded(
                  child: Text(
                    '已阅读并同意《用户协议》与《隐私政策》',
                    style: TextStyle(fontSize: 12.5, color: AppTheme.muted),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 22),
          SizedBox(
            height: 50,
            child: FilledButton(
              onPressed: _loading ? null : _submit,
              style: FilledButton.styleFrom(backgroundColor: AppTheme.brand),
              child: _loading
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white),
                    )
                  : const Text('注册并登录'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _field(
    TextEditingController controller,
    String label,
    IconData icon, {
    bool obscure = false,
    TextInputType? keyboard,
    Widget? suffix,
  }) {
    return TextField(
      controller: controller,
      obscureText: obscure,
      keyboardType: keyboard,
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: Icon(icon, color: AppTheme.muted),
        suffixIcon: suffix,
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
      ),
    );
  }
}
