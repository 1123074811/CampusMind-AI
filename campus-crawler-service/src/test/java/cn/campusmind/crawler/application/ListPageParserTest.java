package cn.campusmind.crawler.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListPageParserTest {

    private final SelectorConfigParser configParser = new SelectorConfigParser(new com.fasterxml.jackson.databind.ObjectMapper());
    private final ListPageParser parser = new ListPageParser();

    @Test
    void parsesXinjiangUniversityMainListTemplate() {
        SelectorConfig config = configParser.parse("""
                {
                  "parserVersion": "xju-main-v1",
                  "list": {
                    "item": ".list1 li",
                    "link": "a[href*=\\"info/\\"]",
                    "title": "a[title], h4",
                    "summary": ".txt p",
                    "date": ".time"
                  }
                }
                """);
        String html = """
                <ul class="list1">
                  <li>
                    <div class="time flex-v-center"><span>07</span>2026/07</div>
                    <a href="../info/1030/28464.htm" title="新疆大学2026年度拟新增本科专业公示">
                      <div class="txt"><h4>新疆大学2026年度拟新增本科专业公示</h4><p>根据自治区教育厅通知...</p></div>
                    </a>
                  </li>
                </ul>
                """;

        ParsedListPage result = parser.parse(html, "https://www.xju.edu.cn/xwzx/tzgg.htm", config);

        assertThat(result.parserVersion()).isEqualTo("xju-main-v1");
        assertThat(result.links()).hasSize(1);
        assertThat(result.links().get(0).title()).isEqualTo("新疆大学2026年度拟新增本科专业公示");
        assertThat(result.links().get(0).url()).isEqualTo("https://www.xju.edu.cn/info/1030/28464.htm");
        assertThat(result.links().get(0).dateText()).contains("2026/07");
        assertThat(result.links().get(0).publishedDate()).isEqualTo(java.time.LocalDate.of(2026, 7, 7));
    }

    @Test
    void parsesSoftwareCollegeListTemplate() {
        SelectorConfig config = configParser.parse("""
                {
                  "parserVersion": "xju-ss-v1",
                  "list": {
                    "link": "a.c130885[href*=\\"info/\\"]",
                    "title": "a.c130885[title]",
                    "date": ".timestyle130885"
                  }
                }
                """);
        String html = """
                <table>
                  <tr>
                    <td><a class="c130885" href="../info/1018/3453.htm" title="软件学院教研室研讨：探索人工智能冲击下本科课程内容的适应性改革">软件学院教研室研讨</a></td>
                    <td><span class="timestyle130885">2026/04/08&nbsp;</span></td>
                  </tr>
                </table>
                """;

        ParsedListPage result = parser.parse(html, "https://ss.xju.edu.cn/xsgz/tzgg.htm", config);

        assertThat(result.parserVersion()).isEqualTo("xju-ss-v1");
        assertThat(result.links()).hasSize(1);
        assertThat(result.links().get(0).title()).isEqualTo("软件学院教研室研讨：探索人工智能冲击下本科课程内容的适应性改革");
        assertThat(result.links().get(0).url()).isEqualTo("https://ss.xju.edu.cn/info/1018/3453.htm");
        assertThat(result.links().get(0).dateText()).isEqualTo("2026/04/08");
        assertThat(result.links().get(0).publishedDate()).isEqualTo(java.time.LocalDate.of(2026, 4, 8));
    }

    @Test
    void parsesAcademicAffairsOfficeListTemplateWithoutNumericCssClasses() {
        SelectorConfig config = configParser.parse("""
                {
                  "parserVersion": "xju-jwc-v1",
                  "list": {
                    "item": "tr:has(a[href*='/info/'][title])",
                    "link": "a[href*='/info/'][title]",
                    "title": "a[href*='/info/'][title]",
                    "date": "span[class^='timestyle']"
                  }
                }
                """);
        String html = """
                <table>
                  <tr>
                    <td><a class="c126031" href="../info/1037/3152.htm" title="日本语能力测试（JLPT）新疆大学考点温馨提示">温馨提示</a></td>
                    <td><span class="timestyle126031">2026/06/23</span></td>
                  </tr>
                </table>
                """;

        ParsedListPage result = parser.parse(html, "https://jwc.xju.edu.cn/tzgg/tzgg.htm", config);

        assertThat(result.parserVersion()).isEqualTo("xju-jwc-v1");
        assertThat(result.links()).hasSize(1);
        assertThat(result.links().get(0).title()).isEqualTo("日本语能力测试（JLPT）新疆大学考点温馨提示");
        assertThat(result.links().get(0).url()).isEqualTo("https://jwc.xju.edu.cn/info/1037/3152.htm");
        assertThat(result.links().get(0).publishedDate()).isEqualTo(java.time.LocalDate.of(2026, 6, 23));
    }
}
