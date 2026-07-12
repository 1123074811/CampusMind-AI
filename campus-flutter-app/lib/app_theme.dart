import 'package:flutter/material.dart';

class AppTheme {
  // Page & Surface
  static const Color bgPage = Color(0xFFE9EDF3);
  static const Color bg = Color(0xFFF5F7FA);
  static const Color surface = Color(0xFFFFFFFF);
  static const Color surface2 = Color(0xFFF1F4F8);

  // Text
  static const Color ink = Color(0xFF171A21);
  static const Color ink2 = Color(0xFF3B404B);
  static const Color muted = Color(0xFF8A909C);

  // Border
  static const Color line = Color(0xFFE7EBF1);

  // Brand
  static const Color brand = Color(0xFF0E9C74);
  static const Color brandInk = Color(0xFF0B7A5A);
  static const Color brandSoft = Color(0xFFE2F6EE);

  // Accent
  static const Color accent = Color(0xFFF59E0B);
  static const Color accentSoft = Color(0xFFFEF3E0);

  // Info
  static const Color info = Color(0xFF3B82F6);
  static const Color infoSoft = Color(0xFFE8F0FE);

  // Rose
  static const Color rose = Color(0xFFF43F5E);
  static const Color roseSoft = Color(0xFFFDEBEF);

  // Radius
  static const double radius = 20.0;
  static const double radiusSm = 13.0;

  // Gradients
  static const LinearGradient brandGradient = LinearGradient(
    colors: [brand, Color(0xFF34D399)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const LinearGradient brandDarkGradient = LinearGradient(
    colors: [brand, brandInk],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );
}
