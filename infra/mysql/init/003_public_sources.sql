SET NAMES utf8mb4;

USE campusmind;

-- A homepage URL alone has no stable list boundary. Disable legacy manual entries
-- without selectors; the section-specific sources below replace it.
UPDATE data_source
SET enabled = 0
WHERE base_url = 'https://jwc.xju.edu.cn/'
  AND selector_config IS NULL;

INSERT INTO data_source (
  name, source_type, base_url, robots_url, crawl_interval_seconds,
  parser_type, selector_config, enabled, last_crawled_at
) VALUES
  (
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
    '新疆大学教务处通知公告',
    'PUBLIC_WEB',
    'https://jwc.xju.edu.cn/tzgg/tzgg.htm',
    'https://jwc.xju.edu.cn/robots.txt',
    1800,
    'WEBMAGIC',
    JSON_OBJECT(
      'parserVersion', 'xju-jwc-v1',
      'host', 'jwc.xju.edu.cn',
      'list', JSON_OBJECT(
        'item', 'tr:has(a[href*="/info/"][title])',
        'link', 'a[href*="/info/"][title]',
        'title', 'a[href*="/info/"][title]',
        'date', 'span[class^="timestyle"]',
        'nextPagePattern', 'https://jwc.xju.edu.cn/tzgg/tzgg/{page}.htm'
      ),
      'detail', JSON_OBJECT(
        'title', 'td[class^="titlestyle"]',
        'meta', 'span[class^="timestyle"]',
        'content', '#vsb_content .v_news_content',
        'publishedAtRegex', '([0-9]{4}-[0-9]{2}-[0-9]{2}(?:\\s+[0-9]{2}:[0-9]{2})?)'
      )
    ),
    1,
    NULL
  ),
  (
    '新疆大学教务处教学快讯',
    'PUBLIC_WEB',
    'https://jwc.xju.edu.cn/jxkx/jxkx.htm',
    'https://jwc.xju.edu.cn/robots.txt',
    1800,
    'WEBMAGIC',
    JSON_OBJECT(
      'parserVersion', 'xju-jwc-v1',
      'host', 'jwc.xju.edu.cn',
      'list', JSON_OBJECT(
        'item', 'tr:has(a[href*="/info/"][title])',
        'link', 'a[href*="/info/"][title]',
        'title', 'a[href*="/info/"][title]',
        'date', 'span[class^="timestyle"]',
        'nextPagePattern', 'https://jwc.xju.edu.cn/jxkx/jxkx/{page}.htm'
      ),
      'detail', JSON_OBJECT(
        'title', 'td[class^="titlestyle"]',
        'meta', 'span[class^="timestyle"]',
        'content', '#vsb_content .v_news_content',
        'publishedAtRegex', '([0-9]{4}-[0-9]{2}-[0-9]{2}(?:\\s+[0-9]{2}:[0-9]{2})?)'
      )
    ),
    1,
    NULL
  ),
  (
    '新疆大学教务处教学改革',
    'PUBLIC_WEB',
    'https://jwc.xju.edu.cn/jxggyyj/jxgg.htm',
    'https://jwc.xju.edu.cn/robots.txt',
    1800,
    'WEBMAGIC',
    JSON_OBJECT(
      'parserVersion', 'xju-jwc-v1',
      'host', 'jwc.xju.edu.cn',
      'list', JSON_OBJECT(
        'item', 'tr:has(a[href*="/info/"][title])',
        'link', 'a[href*="/info/"][title]',
        'title', 'a[href*="/info/"][title]',
        'date', 'span[class^="timestyle"]'
      ),
      'detail', JSON_OBJECT(
        'title', 'td[class^="titlestyle"]',
        'meta', 'span[class^="timestyle"]',
        'content', '#vsb_content .v_news_content',
        'publishedAtRegex', '([0-9]{4}-[0-9]{2}-[0-9]{2}(?:\\s+[0-9]{2}:[0-9]{2})?)'
      )
    ),
    1,
    NULL
  ),
  (
    '新疆大学教务处办事流程',
    'PUBLIC_WEB',
    'https://jwc.xju.edu.cn/fwck/bslc.htm',
    'https://jwc.xju.edu.cn/robots.txt',
    1800,
    'WEBMAGIC',
    JSON_OBJECT(
      'parserVersion', 'xju-jwc-v1',
      'host', 'jwc.xju.edu.cn',
      'list', JSON_OBJECT(
        'item', 'tr:has(a[href*="/info/"][title])',
        'link', 'a[href*="/info/"][title]',
        'title', 'a[href*="/info/"][title]',
        'date', 'span[class^="timestyle"]',
        'nextPagePattern', 'https://jwc.xju.edu.cn/fwck/bslc/{page}.htm'
      ),
      'detail', JSON_OBJECT(
        'title', 'td[class^="titlestyle"]',
        'meta', 'span[class^="timestyle"]',
        'content', '#vsb_content .v_news_content',
        'publishedAtRegex', '([0-9]{4}-[0-9]{2}-[0-9]{2}(?:\\s+[0-9]{2}:[0-9]{2})?)'
      )
    ),
    1,
    NULL
  ),
  (
    '新疆大学教务处常用下载',
    'PUBLIC_WEB',
    'https://jwc.xju.edu.cn/fwck/cyxz.htm',
    'https://jwc.xju.edu.cn/robots.txt',
    1800,
    'WEBMAGIC',
    JSON_OBJECT(
      'parserVersion', 'xju-jwc-v1',
      'host', 'jwc.xju.edu.cn',
      'list', JSON_OBJECT(
        'item', 'tr:has(a[href*="/info/"][title])',
        'link', 'a[href*="/info/"][title]',
        'title', 'a[href*="/info/"][title]',
        'date', 'span[class^="timestyle"]',
        'nextPagePattern', 'https://jwc.xju.edu.cn/fwck/cyxz/{page}.htm'
      ),
      'detail', JSON_OBJECT(
        'title', 'td[class^="titlestyle"]',
        'meta', 'span[class^="timestyle"]',
        'content', '#vsb_content .v_news_content',
        'publishedAtRegex', '([0-9]{4}-[0-9]{2}-[0-9]{2}(?:\\s+[0-9]{2}:[0-9]{2})?)'
      )
    ),
    1,
    NULL
  ),
  (
    '新疆大学教务处考试资讯',
    'PUBLIC_WEB',
    'https://jwc.xju.edu.cn/jyks1/kszx.htm',
    'https://jwc.xju.edu.cn/robots.txt',
    1800,
    'WEBMAGIC',
    JSON_OBJECT(
      'parserVersion', 'xju-jwc-v1',
      'host', 'jwc.xju.edu.cn',
      'list', JSON_OBJECT(
        'item', 'tr:has(a[href*="/info/"][title])',
        'link', 'a[href*="/info/"][title]',
        'title', 'a[href*="/info/"][title]',
        'date', 'span[class^="timestyle"]'
      ),
      'detail', JSON_OBJECT(
        'title', 'td[class^="titlestyle"]',
        'meta', 'span[class^="timestyle"]',
        'content', '#vsb_content .v_news_content',
        'publishedAtRegex', '([0-9]{4}-[0-9]{2}-[0-9]{2}(?:\\s+[0-9]{2}:[0-9]{2})?)'
      )
    ),
    1,
    NULL
  ),
  (
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
  )
AS seed
ON DUPLICATE KEY UPDATE
  name = seed.name,
  source_type = seed.source_type,
  robots_url = seed.robots_url,
  crawl_interval_seconds = seed.crawl_interval_seconds,
  parser_type = seed.parser_type,
  selector_config = seed.selector_config,
  enabled = seed.enabled;
