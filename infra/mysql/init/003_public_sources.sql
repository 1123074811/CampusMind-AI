SET NAMES utf8mb4;

USE campusmind;

DELETE FROM data_source
WHERE id BETWEEN 9411 AND 9417
   OR base_url IN (
    'https://www.xju.edu.cn/xwzx/tzgg.htm',
    'https://www.xju.edu.cn/xwzx/xyjw.htm',
    'https://www.xju.edu.cn/xwzx/xdtt.htm',
    'https://ss.xju.edu.cn/xsgz/tzgg.htm',
    'https://ss.xju.edu.cn/zsjy.htm',
    'https://ss.xju.edu.cn/cxcy/tzgg.htm',
    'https://ss.xju.edu.cn/jwjx.htm'
   );

INSERT INTO data_source (
  id, name, source_type, base_url, robots_url, crawl_interval_seconds,
  parser_type, selector_config, enabled, last_crawled_at
) VALUES
  (
    9411,
    '新疆大学通知公告',
    'PUBLIC_WEB',
    'https://www.xju.edu.cn/xwzx/tzgg.htm',
    'https://www.xju.edu.cn/robots.txt',
    10,
    'WEBMAGIC',
    JSON_OBJECT(
      'parserVersion', 'xju-main-v1',
      'host', 'www.xju.edu.cn',
      'list', JSON_OBJECT(
        'item', '.list1 li',
        'link', 'a[href*="info/"]',
        'title', 'a[title], h4',
        'summary', '.txt p',
        'date', '.time',
        'nextPagePattern', 'https://www.xju.edu.cn/xwzx/tzgg/{page}.htm'
      ),
      'detail', JSON_OBJECT(
        'title', '.arc-tit h1',
        'meta', '.arc-info',
        'content', '#vsb_content .v_news_content',
        'publishedAtRegex', '信息日期[:：]\\s*([0-9]{4}-[0-9]{2}-[0-9]{2}\\s+[0-9]{2}:[0-9]{2}:[0-9]{2})'
      )
    ),
    1,
    NULL
  ),
  (
    9412,
    '新疆大学校园经纬',
    'PUBLIC_WEB',
    'https://www.xju.edu.cn/xwzx/xyjw.htm',
    'https://www.xju.edu.cn/robots.txt',
    10,
    'WEBMAGIC',
    JSON_OBJECT(
      'parserVersion', 'xju-main-v1',
      'host', 'www.xju.edu.cn',
      'list', JSON_OBJECT(
        'item', '.list1 li',
        'link', 'a[href*="info/"]',
        'title', 'a[title], h4',
        'summary', '.txt p',
        'date', '.time',
        'nextPagePattern', 'https://www.xju.edu.cn/xwzx/xyjw/{page}.htm'
      ),
      'detail', JSON_OBJECT(
        'title', '.arc-tit h1',
        'meta', '.arc-info',
        'content', '#vsb_content .v_news_content',
        'publishedAtRegex', '信息日期[:：]\\s*([0-9]{4}-[0-9]{2}-[0-9]{2}\\s+[0-9]{2}:[0-9]{2}:[0-9]{2})'
      )
    ),
    1,
    NULL
  ),
  (
    9413,
    '新疆大学新大头条',
    'PUBLIC_WEB',
    'https://www.xju.edu.cn/xwzx/xdtt.htm',
    'https://www.xju.edu.cn/robots.txt',
    10,
    'WEBMAGIC',
    JSON_OBJECT(
      'parserVersion', 'xju-main-v1',
      'host', 'www.xju.edu.cn',
      'list', JSON_OBJECT(
        'item', '.list1 li',
        'link', 'a[href*="info/"]',
        'title', 'a[title], h4',
        'summary', '.txt p',
        'date', '.time',
        'nextPagePattern', 'https://www.xju.edu.cn/xwzx/xdtt/{page}.htm'
      ),
      'detail', JSON_OBJECT(
        'title', '.arc-tit h1',
        'meta', '.arc-info',
        'content', '#vsb_content .v_news_content',
        'publishedAtRegex', '信息日期[:：]\\s*([0-9]{4}-[0-9]{2}-[0-9]{2}\\s+[0-9]{2}:[0-9]{2}:[0-9]{2})'
      )
    ),
    1,
    NULL
  ),
  (
    9414,
    '软件学院学生工作通知公告',
    'PUBLIC_WEB',
    'https://ss.xju.edu.cn/xsgz/tzgg.htm',
    'https://ss.xju.edu.cn/robots.txt',
    10,
    'WEBMAGIC',
    JSON_OBJECT(
      'parserVersion', 'xju-ss-v1',
      'host', 'ss.xju.edu.cn',
      'list', JSON_OBJECT(
        'link', 'a.c130885[href*="info/"]',
        'title', 'a.c130885[title]',
        'date', '.timestyle130885',
        'nextPagePattern', 'https://ss.xju.edu.cn/xsgz/tzgg/{page}.htm'
      ),
      'detail', JSON_OBJECT(
        'title', 'td.titlestyle130886',
        'content', '#vsb_content .v_news_content',
        'publishedAtRegex', '([0-9]{4}-[0-9]{2}-[0-9]{2}\\s+[0-9]{2}:[0-9]{2})'
      )
    ),
    1,
    NULL
  ),
  (
    9415,
    '软件学院招生就业',
    'PUBLIC_WEB',
    'https://ss.xju.edu.cn/zsjy.htm',
    'https://ss.xju.edu.cn/robots.txt',
    10,
    'WEBMAGIC',
    JSON_OBJECT(
      'parserVersion', 'xju-ss-v1',
      'host', 'ss.xju.edu.cn',
      'list', JSON_OBJECT(
        'link', 'a.c130885[href*="info/"]',
        'title', 'a.c130885[title]',
        'date', '.timestyle130885',
        'nextPagePattern', 'https://ss.xju.edu.cn/zsjy/{page}.htm'
      ),
      'detail', JSON_OBJECT(
        'title', 'td.titlestyle130886',
        'content', '#vsb_content .v_news_content',
        'publishedAtRegex', '([0-9]{4}-[0-9]{2}-[0-9]{2}\\s+[0-9]{2}:[0-9]{2})'
      )
    ),
    1,
    NULL
  ),
  (
    9416,
    '软件学院创新创业通知公告',
    'PUBLIC_WEB',
    'https://ss.xju.edu.cn/cxcy/tzgg.htm',
    'https://ss.xju.edu.cn/robots.txt',
    10,
    'WEBMAGIC',
    JSON_OBJECT(
      'parserVersion', 'xju-ss-v1',
      'host', 'ss.xju.edu.cn',
      'list', JSON_OBJECT(
        'link', 'a.c130885[href*="info/"]',
        'title', 'a.c130885[title]',
        'date', '.timestyle130885',
        'nextPagePattern', 'https://ss.xju.edu.cn/cxcy/tzgg/{page}.htm'
      ),
      'detail', JSON_OBJECT(
        'title', 'td.titlestyle130886',
        'content', '#vsb_content .v_news_content',
        'publishedAtRegex', '([0-9]{4}-[0-9]{2}-[0-9]{2}\\s+[0-9]{2}:[0-9]{2})'
      )
    ),
    1,
    NULL
  ),
  (
    9417,
    '软件学院教务教学',
    'PUBLIC_WEB',
    'https://ss.xju.edu.cn/jwjx.htm',
    'https://ss.xju.edu.cn/robots.txt',
    10,
    'WEBMAGIC',
    JSON_OBJECT(
      'parserVersion', 'xju-ss-v1',
      'host', 'ss.xju.edu.cn',
      'list', JSON_OBJECT(
        'link', 'a.c130885[href*="info/"]',
        'title', 'a.c130885[title]',
        'date', '.timestyle130885',
        'nextPagePattern', 'https://ss.xju.edu.cn/jwjx/{page}.htm'
      ),
      'detail', JSON_OBJECT(
        'title', 'td.titlestyle130886',
        'content', '#vsb_content .v_news_content',
        'publishedAtRegex', '([0-9]{4}-[0-9]{2}-[0-9]{2}\\s+[0-9]{2}:[0-9]{2})'
      )
    ),
    1,
    NULL
  );
