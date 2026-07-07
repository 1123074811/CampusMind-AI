package cn.campusmind.crawler.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SelectorConfig {

    private String parserVersion;
    private String host;
    private ListSelector list = new ListSelector();
    private DetailSelector detail = new DetailSelector();

    public String getParserVersion() {
        return parserVersion;
    }

    public void setParserVersion(String parserVersion) {
        this.parserVersion = parserVersion;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public ListSelector getList() {
        return list;
    }

    public void setList(ListSelector list) {
        this.list = list;
    }

    public DetailSelector getDetail() {
        return detail;
    }

    public void setDetail(DetailSelector detail) {
        this.detail = detail;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListSelector {

        private String item;
        private String link;
        private String title;
        private String summary;
        private String date;
        private String nextPagePattern;

        public String getItem() {
            return item;
        }

        public void setItem(String item) {
            this.item = item;
        }

        public String getLink() {
            return link;
        }

        public void setLink(String link) {
            this.link = link;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getNextPagePattern() {
            return nextPagePattern;
        }

        public void setNextPagePattern(String nextPagePattern) {
            this.nextPagePattern = nextPagePattern;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DetailSelector {

        private String title;
        private String meta;
        private String content;
        private String publishedAtRegex;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getMeta() {
            return meta;
        }

        public void setMeta(String meta) {
            this.meta = meta;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getPublishedAtRegex() {
            return publishedAtRegex;
        }

        public void setPublishedAtRegex(String publishedAtRegex) {
            this.publishedAtRegex = publishedAtRegex;
        }
    }
}
