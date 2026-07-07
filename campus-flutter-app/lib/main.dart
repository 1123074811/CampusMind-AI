import 'package:flutter/material.dart';

void main() {
  runApp(const CampusMindApp());
}

class CampusMindApp extends StatelessWidget {
  const CampusMindApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      title: 'CampusMind',
      home: Scaffold(
        body: Center(
          child: Text('CampusMind'),
        ),
      ),
    );
  }
}

