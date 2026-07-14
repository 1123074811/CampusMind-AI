import 'package:flutter/material.dart';

import 'app_theme.dart';
import 'information_api.dart';

class ChangePasswordPage extends StatefulWidget {
  const ChangePasswordPage({
    super.key,
    required this.api,
    required this.session,
  });

  final CampusApi api;
  final LoginSession session;

  @override
  State<ChangePasswordPage> createState() => _ChangePasswordPageState();
}

class _ChangePasswordPageState extends State<ChangePasswordPage> {
  final _currentCtrl = TextEditingController();
  final _newCtrl = TextEditingController();
  final _confirmCtrl = TextEditingController();
  bool _obscure = true;
  bool _loading = false;

  @override
  void dispose() {
    _currentCtrl.dispose();
    _newCtrl.dispose();
    _confirmCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final current = _currentCtrl.text;
    final next = _newCtrl.text;
    if (current.isEmpty || next.length < 6) {
      _toast('请填写当前密码，且新密码至少 6 位');
      return;
    }
    if (next != _confirmCtrl.text) {
      _toast('两次输入的新密码不一致');
      return;
    }
    if (current == next) {
      _toast('新密码不能与当前密码相同');
      return;
    }
    setState(() => _loading = true);
    try {
      await widget.api.changePassword(current, next, widget.session);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('密码已修改，请重新登录')),
      );
      Navigator.of(context).pop(true);
    } catch (error) {
      _toast('修改失败：$error');
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
        title: const Text('修改密码'),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(22, 8, 22, 28),
        children: [
          const Text(
            '修改成功后将撤销旧会话，需使用新密码重新登录。',
            style: TextStyle(fontSize: 13, color: AppTheme.muted),
          ),
          const SizedBox(height: 18),
          _field(_currentCtrl, '当前密码'),
          const SizedBox(height: 12),
          _field(_newCtrl, '新密码（至少 6 位）'),
          const SizedBox(height: 12),
          _field(_confirmCtrl, '确认新密码'),
          const SizedBox(height: 8),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton(
              onPressed: () => setState(() => _obscure = !_obscure),
              child: Text(_obscure ? '显示密码' : '隐藏密码'),
            ),
          ),
          const SizedBox(height: 12),
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
                  : const Text('确认修改'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _field(TextEditingController controller, String label) {
    return TextField(
      controller: controller,
      obscureText: _obscure,
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: const Icon(Icons.lock_outline, color: AppTheme.muted),
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
