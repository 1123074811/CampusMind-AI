import 'dart:async';
import 'package:flutter/material.dart';
import 'app_theme.dart';

class SplashPage extends StatefulWidget {
  const SplashPage({super.key, required this.onFinished});
  final VoidCallback onFinished;

  @override
  State<SplashPage> createState() => _SplashPageState();
}

class _SplashPageState extends State<SplashPage> {
  late final Timer _timer;

  @override
  void initState() {
    super.initState();
    _timer = Timer(const Duration(milliseconds: 2200), widget.onFinished);
  }

  @override
  void dispose() {
    _timer.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: widget.onFinished,
      child: Scaffold(
        backgroundColor: AppTheme.bg,
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 86,
                height: 86,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(26),
                  gradient: AppTheme.brandGradient,
                  boxShadow: [
                    BoxShadow(
                      color: AppTheme.brand.withValues(alpha: 0.4),
                      blurRadius: 30,
                      offset: const Offset(0, 14),
                    ),
                  ],
                ),
                child: const Icon(Icons.auto_awesome, color: Colors.white, size: 40),
              ),
              const SizedBox(height: 22),
              const Text(
                '校园信息助手',
                style: TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.w800,
                  color: AppTheme.ink,
                  letterSpacing: -0.2,
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                'AI 驱动 · 千人千面的信息聚合',
                style: TextStyle(fontSize: 13, color: AppTheme.muted),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: 150,
                height: 5,
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(99),
                  child: const LinearProgressIndicator(
                    backgroundColor: AppTheme.surface2,
                    valueColor: AlwaysStoppedAnimation<Color>(AppTheme.brand),
                  ),
                ),
              ),
              const SizedBox(height: 14),
              const Text(
                '正在初始化智能体…',
                style: TextStyle(fontSize: 11.5, color: AppTheme.muted),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
