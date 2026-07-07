package cn.campusmind.crawler.application;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ListPageParser {

    public ParsedListPage parse(String html, String baseUrl, SelectorConfig selectorConfig) {
        Document document = Jsoup.parse(html, baseUrl);
        SelectorConfig.ListSelector listSelector = selectorConfig.getList();
        Elements scopes = selectScopes(document, listSelector);
        Map<String, CrawledLink> links = new LinkedHashMap<>();
        for (Element scope : scopes) {
            Element linkElement = first(scope, listSelector.getLink());
            if (linkElement == null) {
                continue;
            }
            String absoluteUrl = linkElement.absUrl("href");
            if (absoluteUrl.isBlank()) {
                continue;
            }
            String title = clean(firstTitle(scope, listSelector.getTitle()));
            if (title.isBlank()) {
                title = clean(linkElement.attr("title"));
            }
            if (title.isBlank()) {
                title = clean(linkElement.text());
            }
            if (title.isBlank()) {
                continue;
            }
            String dateText = normalizeDateText(first(scope, listSelector.getDate()));
            if (dateText.isBlank()) {
                dateText = normalizeDateText(firstInAncestors(scope, listSelector.getDate()));
            }
            String summary = clean(firstText(scope, listSelector.getSummary()));
            links.putIfAbsent(absoluteUrl, new CrawledLink(title, absoluteUrl, dateText, summary));
        }
        return new ParsedListPage(selectorConfig.getParserVersion(), new ArrayList<>(links.values()));
    }

    private Elements selectScopes(Document document, SelectorConfig.ListSelector listSelector) {
        if (listSelector.getItem() != null && !listSelector.getItem().isBlank()) {
            Elements elements = document.select(listSelector.getItem());
            if (!elements.isEmpty()) {
                return elements;
            }
        }
        return document.select(listSelector.getLink());
    }

    private Element first(Element scope, String selector) {
        if (selector == null || selector.isBlank()) {
            return null;
        }
        Elements elements = scope.select(selector);
        return elements.isEmpty() ? null : elements.first();
    }

    private String firstText(Element scope, String selector) {
        Element element = first(scope, selector);
        return element == null ? "" : element.text();
    }

    private String firstTextInAncestors(Element scope, String selector) {
        Element element = firstInAncestors(scope, selector);
        return element == null ? "" : element.text();
    }

    private Element firstInAncestors(Element scope, String selector) {
        if (selector == null || selector.isBlank()) {
            return null;
        }
        Element current = scope.parent();
        for (int depth = 0; current != null && depth < 4; depth++) {
            Element element = first(current, selector);
            if (element != null) {
                return element;
            }
            current = current.parent();
        }
        return null;
    }

    private String firstTitle(Element scope, String selector) {
        Element element = first(scope, selector);
        if (element == null) {
            return "";
        }
        String title = clean(element.attr("title"));
        return title.isBlank() ? element.text() : title;
    }

    private String normalizeDateText(Element element) {
        if (element == null) {
            return "";
        }
        Element dayElement = element.selectFirst("span");
        if (dayElement != null) {
            String day = clean(dayElement.text());
            String yearMonth = clean(element.ownText());
            if (yearMonth.matches("\\d{4}/\\d{2}") && day.matches("\\d{1,2}")) {
                return yearMonth + "/" + String.format("%02d", Integer.parseInt(day));
            }
        }
        return clean(element.text());
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
