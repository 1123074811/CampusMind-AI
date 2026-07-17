import 'package:campus_flutter_app/information_api_stub.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('parses grounded answer and traceable sources', () {
    final result = AiChatResult.fromJson({
      'sessionId': 'session-1',
      'answer': '今天有一场讲座。',
      'grounded': true,
      'retrievalMode': 'VECTOR_LLM',
      'sources': [
        {
          'businessId': 12,
          'docId': 'info-12',
          'title': '人工智能讲座',
          'sourceName': '软件学院',
          'originalUrl': 'https://example.edu/notice/12',
          'score': 0.87,
        }
      ],
    });

    expect(result.grounded, isTrue);
    expect(result.retrievalMode, 'VECTOR_LLM');
    expect(result.sources, hasLength(1));
    expect(result.sources.single.businessId, 12);
    expect(result.sources.single.originalUrl, 'https://example.edu/notice/12');
    expect(result.sources.single.score, 0.87);
  });

  test('keeps backward-compatible defaults when trace fields are absent', () {
    final result = AiChatResult.fromJson({'answer': '你好'});

    expect(result.grounded, isFalse);
    expect(result.retrievalMode, 'NONE');
    expect(result.sources, isEmpty);
  });
}
