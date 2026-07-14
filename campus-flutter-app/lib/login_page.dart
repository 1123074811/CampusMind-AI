import 'package:flutter/material.dart';
import 'app_theme.dart';
import 'information_api.dart';

class PrototypeLoginPage extends StatefulWidget {
  const PrototypeLoginPage({super.key, required this.api, required this.onLogin});
  final CampusApi api;
  final ValueChanged<LoginSession> onLogin;

  @override
  State<PrototypeLoginPage> createState() => _PrototypeLoginPageState();
}

class _PrototypeLoginPageState extends State<PrototypeLoginPage> {
  final _usernameCtrl = TextEditingController(text: '123456');
  final _passwordCtrl = TextEditingController(text: '123456');
  bool _obscure = true;
  bool _agreed = false;
  bool _loading = false;

  @override
  void dispose() {
    _usernameCtrl.dispose();
    _passwordCtrl.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    if (_loading) return;
    if (!_agreed) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('请先阅读并同意用户协议与隐私政策')),
      );
      return;
    }
    setState(() => _loading = true);
    try {
      final session = await widget.api.login(
        _usernameCtrl.text.trim(),
        _passwordCtrl.text,
      );
      if (!mounted) return;
      widget.onLogin(session);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('登录失败：$e')),
      );
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _register() async {
    if (!_agreed) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('注册前请先阅读并同意用户协议与隐私政策')),
      );
      return;
    }
    final username = TextEditingController();
    final email = TextEditingController();
    final password = TextEditingController();
    final submitted = await showDialog<bool>(context: context, builder: (context) => AlertDialog(
      title: const Text('注册学生账号'),
      content: Column(mainAxisSize: MainAxisSize.min, children: [
        TextField(controller: username, decoration: const InputDecoration(labelText: '用户名')),
        TextField(controller: email, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: '邮箱')),
        TextField(controller: password, obscureText: true, decoration: const InputDecoration(labelText: '密码（至少 6 位）')),
      ]),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
        FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('注册')),
      ],
    ));
    if (submitted != true) { username.dispose(); email.dispose(); password.dispose(); return; }
    try {
      final session = await widget.api.register(username.text.trim(), email.text.trim(), password.text);
      await widget.api.updateConsent('PRIVACY_POLICY', true, '2026-07-01', session);
      await widget.api.updateConsent('PERSONALIZATION', true, '2026-07-01', session);
      if (!mounted) return;
      widget.onLogin(session);
    } catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('注册失败：$error')));
    } finally {
      username.dispose(); email.dispose(); password.dispose();
    }
  }

  Future<void> _forgotPassword() async {
    final account = TextEditingController(text: _usernameCtrl.text.trim());
    final requested = await showDialog<bool>(context: context, builder: (context) => AlertDialog(
      title: const Text('找回密码'),
      content: TextField(controller: account, decoration: const InputDecoration(labelText: '用户名或邮箱')),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
        FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('发送重置链接')),
      ],
    ));
    if (requested != true) { account.dispose(); return; }
    try {
      final developmentToken = await widget.api.forgotPassword(account.text.trim());
      if (!mounted) return;
      final token = TextEditingController(text: developmentToken ?? '');
      final password = TextEditingController();
      final reset = await showDialog<bool>(context: context, builder: (context) => AlertDialog(
        title: const Text('设置新密码'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          Text(developmentToken == null ? '重置链接已发送至绑定邮箱。' : '开发环境已自动填入一次性令牌。'),
          TextField(controller: token, decoration: const InputDecoration(labelText: '重置令牌')),
          TextField(controller: password, obscureText: true, decoration: const InputDecoration(labelText: '新密码（至少 6 位）')),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('稍后')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('确认重置')),
        ],
      ));
      if (reset == true) {
        await widget.api.resetPassword(token.text.trim(), password.text);
        if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('密码已重置，请重新登录')));
      }
      token.dispose(); password.dispose();
    } catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('找回密码失败：$error')));
    } finally { account.dispose(); }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(26, 18, 26, 26),
          children: [
            const SizedBox(height: 24),
            // Logo
            Center(
              child: Container(
                width: 66,
                height: 66,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(20),
                  gradient: AppTheme.brandGradient,
                  boxShadow: [
                    BoxShadow(
                      color: AppTheme.brand.withValues(alpha: 0.35),
                      blurRadius: 26,
                      offset: const Offset(0, 12),
                    ),
                  ],
                ),
                child: const Icon(Icons.auto_awesome, color: Colors.white, size: 32),
              ),
            ),
            const SizedBox(height: 20),
            const Center(
              child: Text(
                '欢迎回来',
                style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: AppTheme.ink),
              ),
            ),
            const SizedBox(height: 8),
            const Center(
              child: Text(
                '登录校园信息助手，开启智能校园生活',
                style: TextStyle(fontSize: 13, color: AppTheme.muted),
              ),
            ),
            const SizedBox(height: 30),
            // Username field
            _InputField(
              icon: Icons.person_outline,
              controller: _usernameCtrl,
              hint: '学号 / 手机号',
            ),
            const SizedBox(height: 14),
            // Password field
            _InputField(
              icon: Icons.lock_outline,
              controller: _passwordCtrl,
              hint: '密码',
              obscure: _obscure,
              suffix: GestureDetector(
                onTap: () => setState(() => _obscure = !_obscure),
                child: Icon(
                  _obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                  color: AppTheme.muted,
                  size: 20,
                ),
              ),
            ),
            const SizedBox(height: 12),
            // Links row
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                TextButton(
                  onPressed: _register,
                  style: TextButton.styleFrom(padding: EdgeInsets.zero, minimumSize: Size.zero),
                  child: const Text('注册账号', style: TextStyle(fontSize: 12.5, color: AppTheme.brandInk, fontWeight: FontWeight.w600)),
                ),
                TextButton(
                  onPressed: _forgotPassword,
                  style: TextButton.styleFrom(padding: EdgeInsets.zero, minimumSize: Size.zero),
                  child: const Text('忘记密码？', style: TextStyle(fontSize: 12.5, color: AppTheme.brandInk, fontWeight: FontWeight.w600)),
                ),
              ],
            ),
            const SizedBox(height: 18),
            // Login button
            SizedBox(
              height: 52,
              child: ElevatedButton(
                onPressed: _loading ? null : _login,
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTheme.brand,
                  foregroundColor: Colors.white,
                  elevation: 0,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  shadowColor: AppTheme.brand.withValues(alpha: 0.35),
                ),
                child: _loading
                    ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                    : const Text('登 录', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
              ),
            ),
            const SizedBox(height: 22),
            // Divider
            Row(
              children: [
                const Expanded(child: Divider(color: AppTheme.line)),
                const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 12),
                  child: Text('其他登录方式', style: TextStyle(fontSize: 12, color: AppTheme.muted)),
                ),
                const Expanded(child: Divider(color: AppTheme.line)),
              ],
            ),
            const SizedBox(height: 18),
            // OAuth buttons
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _OAuthBtn(icon: Icons.school_outlined, tooltip: '校园统一账号'),
                const SizedBox(width: 14),
                _OAuthBtn(icon: Icons.chat_bubble_outline, tooltip: '微信'),
              ],
            ),
            const SizedBox(height: 24),
            // Agreement
            GestureDetector(
              onTap: () => setState(() => _agreed = !_agreed),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 16,
                    height: 16,
                    margin: const EdgeInsets.only(top: 2),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(5),
                      border: Border.all(color: _agreed ? AppTheme.brand : AppTheme.muted, width: 1.5),
                      color: _agreed ? AppTheme.brand : Colors.transparent,
                    ),
                    child: _agreed ? const Icon(Icons.check, size: 12, color: Colors.white) : null,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text.rich(
                      TextSpan(
                        style: const TextStyle(fontSize: 11.5, color: AppTheme.muted, height: 1.5),
                        children: [
                          const TextSpan(text: '已阅读并同意 '),
                          TextSpan(text: '《用户协议》', style: TextStyle(color: AppTheme.brandInk, fontWeight: FontWeight.w600)),
                          const TextSpan(text: ' 与 '),
                          TextSpan(text: '《隐私政策》', style: TextStyle(color: AppTheme.brandInk, fontWeight: FontWeight.w600)),
                          const TextSpan(text: '，授权 AI 智能体基于你的偏好进行信息聚合与推荐。'),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _InputField extends StatelessWidget {
  const _InputField({
    required this.icon,
    required this.controller,
    required this.hint,
    this.obscure = false,
    this.suffix,
  });
  final IconData icon;
  final TextEditingController controller;
  final String hint;
  final bool obscure;
  final Widget? suffix;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppTheme.line, width: 1.5),
      ),
      child: TextField(
        controller: controller,
        obscureText: obscure,
        style: const TextStyle(fontSize: 14, color: AppTheme.ink),
        decoration: InputDecoration(
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
          prefixIcon: Icon(icon, color: AppTheme.muted, size: 20),
          suffixIcon: suffix != null
              ? Padding(padding: const EdgeInsets.only(right: 12), child: suffix)
              : null,
          hintText: hint,
          hintStyle: const TextStyle(fontSize: 14, color: AppTheme.muted),
        ),
      ),
    );
  }
}

class _OAuthBtn extends StatelessWidget {
  const _OAuthBtn({required this.icon, required this.tooltip});
  final IconData icon;
  final String tooltip;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 52,
      height: 52,
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(15),
        border: Border.all(color: AppTheme.line),
      ),
      child: Icon(icon, color: AppTheme.brandInk, size: 22),
    );
  }
}
