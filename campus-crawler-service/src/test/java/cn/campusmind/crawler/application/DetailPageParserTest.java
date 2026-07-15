package cn.campusmind.crawler.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DetailPageParserTest {

    private final SelectorConfigParser configParser = new SelectorConfigParser(new com.fasterxml.jackson.databind.ObjectMapper());
    private final DetailPageParser parser = new DetailPageParser();

    @Test
    void parsesXinjiangUniversityMainDetailTemplate() {
        SelectorConfig config = configParser.parse("""
                {
                  "detail": {
                    "title": ".arc-tit h1",
                    "meta": ".arc-info",
                    "content": "#vsb_content .v_news_content",
                    "publishedAtRegex": "信息日期[:：]\\\\s*([0-9]{4}-[0-9]{2}-[0-9]{2}\\\\s+[0-9]{2}:[0-9]{2}:[0-9]{2})"
                  }
                }
                """);
        String html = """
                <div class="arc-tit"><h1>新疆大学2026年度拟新增本科专业公示</h1></div>
                <div class="arc-info">信息日期：2026-07-07 15:57:55</div>
                <div id="vsb_content"><div class="v_news_content">
                  <p>根据自治区教育厅通知，学校组织专家组对申报专业进行评审。</p>
                </div></div>
                """;

        ParsedDetailPage result = parser.parse(html, "https://www.xju.edu.cn/info/1030/28464.htm", config);

        assertThat(result.status()).isEqualTo("DETAIL_SUCCESS");
        assertThat(result.title()).isEqualTo("新疆大学2026年度拟新增本科专业公示");
        assertThat(result.publishedAtText()).isEqualTo("2026-07-07 15:57:55");
        assertThat(result.content()).contains("学校组织专家组");
    }

    @Test
    void parsesSoftwareCollegeDetailTemplate() {
        SelectorConfig config = configParser.parse("""
                {
                  "detail": {
                    "title": "td.titlestyle130886",
                    "content": "#vsb_content .v_news_content"
                  }
                }
                """);
        String html = """
                <table><tr><td class="titlestyle130886">软件学院教研室研讨</td></tr></table>
                <div>发布时间：2026-04-08 14:30</div>
                <div id="vsb_content"><div class="v_news_content">
                  <p>软件学院于4月8日开展课程内容改革研讨。</p>
                </div></div>
                """;

        ParsedDetailPage result = parser.parse(html, "https://ss.xju.edu.cn/info/1018/3453.htm", config);

        assertThat(result.status()).isEqualTo("DETAIL_SUCCESS");
        assertThat(result.title()).isEqualTo("软件学院教研室研讨");
        assertThat(result.publishedAtText()).isEqualTo("2026-04-08 14:30");
        assertThat(result.content()).contains("课程内容改革研讨");
    }

    @Test
    void parsesAcademicAffairsOfficeDetailTemplateWithoutNumericCssClasses() {
        SelectorConfig config = configParser.parse("""
                {
                  "detail": {
                    "title": "td[class^='titlestyle']",
                    "meta": "span[class^='timestyle']",
                    "content": "#vsb_content .v_news_content",
                    "publishedAtRegex": "([0-9]{4}-[0-9]{2}-[0-9]{2}(?:\\\\s+[0-9]{2}:[0-9]{2})?)"
                  }
                }
                """);
        String html = """
                <table><tr><td class="titlestyle126024">日本语能力测试（JLPT）新疆大学考点温馨提示</td></tr></table>
                <span class="timestyle126024">2026-06-23 18:44</span>
                <div id="vsb_newscontent"><div id="vsb_content"><div class="v_news_content">
                  <p>请考生按通知要求携带准考证和有效身份证件参加考试。</p>
                </div></div></div>
                """;

        ParsedDetailPage result = parser.parse(html, "https://jwc.xju.edu.cn/info/1037/3152.htm", config);

        assertThat(result.status()).isEqualTo("DETAIL_SUCCESS");
        assertThat(result.title()).isEqualTo("日本语能力测试（JLPT）新疆大学考点温馨提示");
        assertThat(result.publishedAtText()).isEqualTo("2026-06-23 18:44");
        assertThat(result.content()).contains("有效身份证件");
    }

    @Test
    void reportsParseFailureWhenContentMissing() {
        SelectorConfig config = configParser.parse("""
                {
                  "detail": {
                    "title": "h1",
                    "content": ".missing-content"
                  }
                }
                """);

        ParsedDetailPage result = parser.parse("<h1>标题</h1>", "https://example.edu/info/1.htm", config);

        assertThat(result.status()).isEqualTo("PARSE_FAILED");
        assertThat(result.error()).contains("正文选择器");
    }

    @Test
    void fallsBackToXinjiangUniversityNewsContentContainer() {
        SelectorConfig config = configParser.parse("""
                {
                  "detail": {
                    "title": ".arc-tit h1",
                    "content": "#vsb_content .v_news_content"
                  }
                }
                """);
        String html = """
                <div class="arc-tit"><h1>深化兵地校际交流 共促财资业务管理</h1></div>
                <div id="vsb_content_501"><div class="v_news_content">
                  <p>2026年7月2日上午，新疆政法学院一行到访新疆大学。</p>
                </div></div>
                """;

        ParsedDetailPage result = parser.parse(html, "https://www.xju.edu.cn/info/1028/28460.htm", config);

        assertThat(result.status()).isEqualTo("DETAIL_SUCCESS");
        assertThat(result.content()).contains("新疆政法学院");
    }

    @Test
    void fallsBackToMetaDescriptionWhenBodyIsEmpty() {
        SelectorConfig config = configParser.parse("""
                {
                  "detail": {
                    "title": "td.titlestyle130886",
                    "content": "#vsb_content .v_news_content"
                  }
                }
                """);
        String html = """
                <head><meta name="description" content="新疆大学2026年博士研究生拟录取名单公示"></head>
                <table><tr><td class="titlestyle130886">博士研究生拟录取名单公示</td></tr></table>
                <div id="vsb_content"><div class="v_news_content"></div></div>
                """;

        ParsedDetailPage result = parser.parse(html, "https://ss.xju.edu.cn/info/1036/3464.htm", config);

        assertThat(result.status()).isEqualTo("DETAIL_SUCCESS");
        assertThat(result.content()).contains("博士研究生");
    }
}
