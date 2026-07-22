import 'package:flutter_test/flutter_test.dart';

import 'package:campus_flutter_app/information_api.dart';

void main() {
  test('雨课堂消息优先按课程标签归组并兼容旧摘要', () {
    final tagged = ImportedEventItem.fromJson({
      'id': 1,
      'title': '作业 1',
      'summary': '课程：旧课程名',
      'eventType': 'HOMEWORK',
      'sourceType': 'RAIN_CLASSROOM',
      'tags': ['雨课堂', '课程:软件工程', '学期:2026春'],
      'publishedAt': '2026-07-08T09:30:00',
      'endTime': '2026-07-18T23:59:00',
    });
    final legacy = ImportedEventItem.fromJson({
      'id': 2,
      'title': '考试通知',
      'summary': '类型：EXAM\n课程：高等数学\n标题：考试通知',
      'eventType': 'EXAM',
      'sourceType': 'RAIN_CLASSROOM',
    });

    expect(tagged.courseName, '软件工程');
    expect(tagged.semester, '2026春');
    expect(tagged.time, DateTime(2026, 7, 8, 9, 30));
    expect(tagged.deadline, DateTime(2026, 7, 18, 23, 59));
    expect(legacy.courseName, '高等数学');
  });

  test('雨课堂学年学期和来源地址可用于补全归组', () {
    final item = ImportedEventItem.fromJson({
      'id': 3,
      'title': '课程通知',
      'summary': '课程：软件工程',
      'eventType': 'NOTICE',
      'sourceType': 'RAIN_CLASSROOM',
      'tags': ['雨课堂', '学期:2023-2024-1'],
      'sources': [
        {'sourceUrl': 'https://www.yuketang.cn/v2/web/studentLog/42'}
      ],
    });

    expect(item.semester, '2023秋');
    expect(item.sourceUrl, 'https://www.yuketang.cn/v2/web/studentLog/42');
  });

  test('缺少课程标签的旧 COURSE 活动按消息处理', () {
    final activity = ImportedEventItem.fromJson({
      'id': 4,
      'title': '计算机系统基础-第10章-系统分析与设计-习题',
      'summary': '类型：COURSE\n标题：计算机系统基础-第10章-系统分析与设计-习题',
      'eventType': 'COURSE',
      'sourceType': 'RAIN_CLASSROOM',
      'tags': ['雨课堂'],
    });
    final course = ImportedEventItem.fromJson({
      'id': 5,
      'title': '计算机系统基础',
      'eventType': 'COURSE',
      'sourceType': 'RAIN_CLASSROOM',
      'tags': ['雨课堂', '课程:计算机系统基础', '学期:2025春'],
    });

    expect(activity.eventType, 'HOMEWORK');
    expect(activity.courseName, '未归类消息');
    expect(course.eventType, 'COURSE');
  });
}
