package cn.campusmind.crawler.application;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DetailPageParser {

    public ParsedDetailPage parse(String html, String url, SelectorConfig selectorConfig) {
        SelectorConfig.DetailSelector detailSelector = selectorConfig.getDetail();
        Document document = Jsoup.parse(html, url);
        String title = clean(firstText(document, detailSelector.getTitle()));
        String publishedAtText = extractPublishedAtText(document, detailSelector);
        if (title.isBlank()) {
            title = clean(document.title());
        }
        String content = clean(firstText(document, detailSelector.getContent()));
        if (content.isBlank()) {
            content = clean(firstText(document, "#vsb_content_501 .v_news_content"));
        }
        if (content.isBlank()) {
            content = clean(firstText(document, "#vsb_newscontent .v_news_content"));
        }
        if (content.isBlank()) {
            content = clean(firstText(document, ".v_news_content"));
        }
        if (content.isBlank()) {
            content = clean(firstText(document, "meta[name=description]"));
        }
        if (content.isBlank()) {
            return new ParsedDetailPage(title, "", publishedAtText, "PARSE_FAILED", "详情页正文选择器未匹配到内容");
        }
        return new ParsedDetailPage(title, content, publishedAtText, "DETAIL_SUCCESS", null);
    }

    private String extractPublishedAtText(Document document, SelectorConfig.DetailSelector detailSelector) {
        String metaText = clean(firstText(document, detailSelector.getMeta()));
        String regex = detailSelector.getPublishedAtRegex();
        if (metaText.isBlank() || regex == null || regex.isBlank()) {
            return "";
        }
        Matcher matcher = Pattern.compile(regex).matcher(metaText);
        if (!matcher.find()) {
            return "";
        }
        return clean(matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group());
    }

    private String firstText(Document document, String selector) {
        if (selector == null || selector.isBlank()) {
            return "";
        }
        Element element = document.selectFirst(selector);
        if (element == null) {
            return "";
        }
        if ("meta".equalsIgnoreCase(element.tagName())) {
            return element.attr("content");
        }
        return element.text();
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
