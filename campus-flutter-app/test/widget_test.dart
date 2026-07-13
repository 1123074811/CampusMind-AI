import 'package:flutter_test/flutter_test.dart';

import 'package:campus_flutter_app/information_api.dart';

void main() {
  test('keeps the persisted AI summary returned by the backend', () {
    final item = InformationItem.fromJson({
      'id': 1,
      'title': '创新创业竞赛报名开放',
      'sourceName': '软件学院',
      'fetchedAt': '2026-07-09T10:00:00',
      'aiSummary': '模型生成的竞赛报名摘要。',
    });

    expect(item.aiSummary, '模型生成的竞赛报名摘要。');
  });
}
