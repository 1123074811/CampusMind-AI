package cn.campusmind.crawler.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.crawler.config.CrawlerProperties;
import cn.campusmind.crawler.controller.CrawlItemResponse;
import cn.campusmind.crawler.domain.CampusEvent;
import cn.campusmind.crawler.domain.CrawlTask;
import cn.campusmind.crawler.domain.DataSource;
import cn.campusmind.crawler.domain.EventSourceRef;
import cn.campusmind.crawler.domain.WebCrawlItem;
import cn.campusmind.crawler.infrastructure.mapper.CampusEventMapper;
import cn.campusmind.crawler.infrastructure.mapper.CrawlTaskMapper;
import cn.campusmind.crawler.infrastructure.mapper.DataSourceMapper;
import cn.campusmind.crawler.infrastructure.mapper.EventSourceRefMapper;
import cn.campusmind.crawler.infrastructure.mapper.WebCrawlItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CrawlerService {

    private static final String PUBLIC_WEB = "PUBLIC_WEB";
    private static final String WEBMAGIC = "WEBMAGIC";
    private static final Pattern FULL_DATE = Pattern.compile("(\\d{4})[年/.-](\\d{1,2})[月/.-](\\d{1,2})");
    private static final Pattern MONTH_DAY = Pattern.compile("(?<!\\d)(\\d{1,2})[月/.-](\\d{1,2})");
    private static final DateTimeFormatter EVENT_DATE = DateTimeFormatter.ofPattern("yyyy-M-d");
    private static final int MAX_FAIL_REASON_LENGTH = 1000;

    private final DataSourceMapper dataSourceMapper;
    private final CrawlTaskMapper crawlTaskMapper;
    private final WebCrawlItemMapper webCrawlItemMapper;
    private final CampusEventMapper campusEventMapper;
    private final EventSourceRefMapper eventSourceRefMapper;
    private final SelectorConfigParser selectorConfigParser;
    private final ListPageParser listPageParser;
    private final DetailPageParser detailPageParser;
    private final PublicWebFetcher publicWebFetcher;
    private final CrawlerProperties crawlerProperties;

    public CrawlerService(DataSourceMapper dataSourceMapper,
                          CrawlTaskMapper crawlTaskMapper,
                          WebCrawlItemMapper webCrawlItemMapper,
                          CampusEventMapper campusEventMapper,
                          EventSourceRefMapper eventSourceRefMapper,
                          SelectorConfigParser selectorConfigParser,
                          ListPageParser listPageParser,
                          DetailPageParser detailPageParser,
                          PublicWebFetcher publicWebFetcher,
                          CrawlerProperties crawlerProperties) {
        this.dataSourceMapper = dataSourceMapper;
        this.crawlTaskMapper = crawlTaskMapper;
        this.webCrawlItemMapper = webCrawlItemMapper;
        this.campusEventMapper = campusEventMapper;
        this.eventSourceRefMapper = eventSourceRefMapper;
        this.selectorConfigParser = selectorConfigParser;
        this.listPageParser = listPageParser;
        this.detailPageParser = detailPageParser;
        this.publicWebFetcher = publicWebFetcher;
        this.crawlerProperties = crawlerProperties;
    }

    @Transactional
    public CrawlSourceResult crawlSource(Long sourceId) {
        return crawlSource(sourceId, new CrawlOptions(30, 50));
    }

    public BatchCrawlResult crawlEnabledSources(CrawlOptions options) {
        LocalDateTime startedAt = LocalDateTime.now();
        List<DataSource> sources = dataSourceMapper.selectList(new LambdaQueryWrapper<DataSource>()
                .eq(DataSource::getEnabled, 1)
                .eq(DataSource::getSourceType, PUBLIC_WEB)
                .eq(DataSource::getParserType, WEBMAGIC)
                .orderByAsc(DataSource::getId));
        List<CrawlSourceResult> results = new ArrayList<>();
        for (DataSource source : sources) {
            results.add(crawlSource(source.getId(), options));
        }
        int successCount = (int) results.stream().filter(result -> "SUCCESS".equals(result.status())).count();
        int persistedCount = results.stream().mapToInt(CrawlSourceResult::persistedCount).sum();
        LocalDateTime finishedAt = LocalDateTime.now();
        return new BatchCrawlResult(sources.size(), successCount, results.size() - successCount,
                persistedCount, results, startedAt, finishedAt);
    }

    @Transactional(readOnly = true)
    public List<CrawlItemResponse> latestItems(Long sourceId, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<WebCrawlItem> items = webCrawlItemMapper.selectList(new LambdaQueryWrapper<WebCrawlItem>()
                .eq(sourceId != null, WebCrawlItem::getSourceId, sourceId)
                .orderByDesc(WebCrawlItem::getFetchedAt)
                .orderByDesc(WebCrawlItem::getId)
                .last("LIMIT " + safeSize));
        return items.stream().map(this::toItemResponse).toList();
    }

    @Transactional
    public int publishExistingItems(Integer size) {
        int safeSize = Math.min(Math.max(size == null ? 500 : size, 1), 1000);
        List<WebCrawlItem> items = webCrawlItemMapper.selectList(new LambdaQueryWrapper<WebCrawlItem>()
                .eq(WebCrawlItem::getParseStatus, "DETAIL_SUCCESS")
                .orderByDesc(WebCrawlItem::getFetchedAt)
                .orderByDesc(WebCrawlItem::getId)
                .last("LIMIT " + safeSize));
        int publishedCount = 0;
        for (WebCrawlItem item : items) {
            DataSource source = dataSourceMapper.selectById(item.getSourceId());
            if (source != null && publishEvent(source, item)) {
                publishedCount++;
            }
        }
        return publishedCount;
    }

    @Transactional
    public CrawlSourceResult crawlSource(Long sourceId, CrawlOptions options) {
        DataSource source = dataSourceMapper.selectById(sourceId);
        if (source == null) {
            throw new BusinessException("SOURCE_NOT_FOUND", "数据源不存在", HttpStatus.NOT_FOUND);
        }
        CrawlTask task = newTask(source);
        crawlTaskMapper.insert(task);
        if (!isSupported(source)) {
            return finish(task, source, "SKIPPED", null, null, 0, 0, List.of(), null,
                    "仅支持启用的 PUBLIC_WEB/WEBMAGIC 数据源");
        }
        if (intervalTooSmall(source)) {
            return finish(task, source, "SKIPPED", null, null, 0, 0, List.of(), null,
                    "采集间隔必须大于 " + crawlerProperties.getMinIntervalSeconds() + " 秒");
        }
        try {
            SelectorConfig selectorConfig = selectorConfigParser.parse(source.getSelectorConfig());
            ListPageCrawl pageCrawl = crawlListPages(source, selectorConfig, options);
            List<CrawledLink> filteredLinks = filterRecentLinks(pageCrawl.links(), options);
            int persistedCount = persistItems(task, source, selectorConfig, pageCrawl.parserVersion(), filteredLinks);
            task.setHttpStatus(pageCrawl.httpStatus());
            task.setEtag(pageCrawl.etag());
            task.setLastModified(pageCrawl.lastModified());
            source.setLastCrawledAt(LocalDateTime.now());
            dataSourceMapper.updateById(source);
            return finish(task, source, "SUCCESS", pageCrawl.httpStatus(), pageCrawl.parserVersion(),
                    pageCrawl.links().size(), persistedCount, filteredLinks, null, null);
        } catch (Exception e) {
            return finish(task, source, "FAILED", task.getHttpStatus(), null, 0, 0, List.of(), e.getMessage(), e.getMessage());
        }
    }

    private List<CrawledLink> filterRecentLinks(List<CrawledLink> links, CrawlOptions options) {
        LocalDate cutoff = LocalDate.now().minusDays(options.normalizedDays() - 1L);
        return links.stream()
                .filter(link -> link.publishedDate() == null || !link.publishedDate().isBefore(cutoff))
                .limit(options.normalizedMaxItems())
                .toList();
    }

    private ListPageCrawl crawlListPages(DataSource source, SelectorConfig selectorConfig, CrawlOptions options) {
        int maxPages = Math.max(crawlerProperties.getMaxPagesPerSource(), 1);
        Map<String, CrawledLink> discovered = new LinkedHashMap<>();
        String nextUrl = source.getBaseUrl();
        String parserVersion = selectorConfig.getParserVersion();
        Integer firstHttpStatus = null;
        String firstEtag = null;
        String firstLastModified = null;
        int page = 1;
        while (StringUtils.hasText(nextUrl) && page <= maxPages && discovered.size() < options.normalizedMaxItems()) {
            PublicWebFetcher.FetchResult fetchResult = publicWebFetcher.fetch(nextUrl);
            if (page == 1) {
                firstHttpStatus = fetchResult.httpStatus();
                firstEtag = fetchResult.etag();
                firstLastModified = fetchResult.lastModified();
            }
            ParsedListPage parsed = listPageParser.parse(fetchResult.body(), nextUrl, selectorConfig);
            parserVersion = parsed.parserVersion();
            int before = discovered.size();
            for (CrawledLink link : parsed.links()) {
                discovered.putIfAbsent(link.url(), link);
            }
            String linkedNext = findNextPageUrl(fetchResult.body(), nextUrl);
            nextUrl = StringUtils.hasText(linkedNext) ? linkedNext : patternedNextPage(selectorConfig, page);
            if (discovered.size() == before && !StringUtils.hasText(linkedNext)) {
                break;
            }
            page++;
        }
        return new ListPageCrawl(parserVersion, new ArrayList<>(discovered.values()),
                firstHttpStatus, firstEtag, firstLastModified);
    }

    private String findNextPageUrl(String html, String baseUrl) {
        if (!StringUtils.hasText(html)) {
            return null;
        }
        Document document = Jsoup.parse(html, baseUrl);
        for (Element link : document.select("a[href]")) {
            if (containsNextPageText(link.text()) || containsNextPageText(link.attr("title"))) {
                String url = link.absUrl("href");
                return url.isBlank() || url.equals(baseUrl) ? null : url;
            }
        }
        return null;
    }

    private boolean containsNextPageText(String value) {
        return value != null && (value.contains("下一页") || value.equalsIgnoreCase("next") || value.equals(">"));
    }

    private String patternedNextPage(SelectorConfig selectorConfig, int currentPage) {
        String pattern = selectorConfig.getList() == null ? null : selectorConfig.getList().getNextPagePattern();
        if (!StringUtils.hasText(pattern)) {
            return null;
        }
        return pattern.replace("{page}", String.valueOf(currentPage));
    }

    private int persistItems(CrawlTask task, DataSource source, SelectorConfig selectorConfig,
                             String parserVersion, List<CrawledLink> links) {
        LocalDateTime fetchedAt = LocalDateTime.now();
        int persistedCount = 0;
        for (CrawledLink link : links) {
            String hash = sha256(source.getId() + "|" + link.title() + "|" + link.url() + "|"
                    + link.dateText() + "|" + link.summary());
            WebCrawlItem item = new WebCrawlItem();
            item.setTaskId(task.getId());
            item.setSourceId(source.getId());
            item.setSourceName(source.getName());
            item.setSourceUrl(source.getBaseUrl());
            item.setItemUrl(link.url());
            item.setTitle(link.title());
            item.setDateText(link.dateText());
            item.setSummary(link.summary());
            item.setContentHash(hash);
            item.setParserVersion(parserVersion);
            enrichDetail(item, link, selectorConfig);
            item.setFetchedAt(fetchedAt);

            WebCrawlItem existing = webCrawlItemMapper.selectOne(new LambdaQueryWrapper<WebCrawlItem>()
                    .eq(WebCrawlItem::getSourceId, source.getId())
                    .eq(WebCrawlItem::getItemUrl, link.url())
                    .last("LIMIT 1"));
            if (existing == null) {
                webCrawlItemMapper.insert(item);
                publishEvent(source, item);
                persistedCount++;
            } else if (contentChanged(existing, item)) {
                item.setId(existing.getId());
                webCrawlItemMapper.updateById(item);
                publishEvent(source, item);
                persistedCount++;
            } else {
                publishEvent(source, existing);
            }
        }
        return persistedCount;
    }

    private void enrichDetail(WebCrawlItem item, CrawledLink link, SelectorConfig selectorConfig) {
        item.setParseStatus("LIST_ONLY");
        try {
            PublicWebFetcher.FetchResult detailFetch = publicWebFetcher.fetch(link.url());
            item.setDetailHttpStatus(detailFetch.httpStatus());
            item.setDetailFetchedAt(LocalDateTime.now());
            ParsedDetailPage detail = detailPageParser.parse(detailFetch.body(), link.url(), selectorConfig);
            item.setDetailTitle(detail.title());
            item.setDetailContent(detail.content());
            item.setParseStatus(detail.status());
            item.setParseError(detail.error());
            if (detail.content() != null && !detail.content().isBlank()) {
                item.setDetailContentHash(sha256(detail.content()));
            }
        } catch (Exception e) {
            item.setParseStatus("DETAIL_FAILED");
            item.setParseError(e.getMessage());
        }
    }

    private boolean contentChanged(WebCrawlItem existing, WebCrawlItem item) {
        return !Objects.equals(existing.getContentHash(), item.getContentHash())
                || !Objects.equals(existing.getDetailContentHash(), item.getDetailContentHash())
                || !Objects.equals(existing.getParseStatus(), item.getParseStatus());
    }

    private boolean publishEvent(DataSource source, WebCrawlItem item) {
        String dedupKey = sha256("PUBLIC_WEB|" + source.getId() + "|" + item.getItemUrl());
        CampusEvent existing = campusEventMapper.selectOne(new LambdaQueryWrapper<CampusEvent>()
                .eq(CampusEvent::getDedupKey, dedupKey)
                .last("LIMIT 1"));
        CampusEvent event = new CampusEvent();
        String eventType = classifyEventType(item);
        event.setTitle(truncate(firstNonBlank(item.getDetailTitle(), item.getTitle()), 255));
        event.setSummary(truncate(firstNonBlank(item.getSummary(), item.getDetailContent(), item.getTitle()), 500));
        event.setEventType(eventType);
        event.setSourceType(PUBLIC_WEB);
        event.setStatus("AI_PUBLISHED");
        event.setConfidence(new BigDecimal("0.6000"));
        event.setOrganizer(source.getName());
        event.setStartTime(parseEventDate(item.getDateText()));
        event.setTargetScope("[\"全校师生\"]");
        event.setTags(tagsJson(eventTypeTag(eventType), source.getName()));
        event.setDedupKey(dedupKey);
        event.setPublishedAt(item.getFetchedAt() == null ? LocalDateTime.now() : item.getFetchedAt());
        if (existing == null) {
            campusEventMapper.insert(event);
            insertSourceRefIfMissing(event.getId(), source, item);
            return true;
        } else if ("AI_PUBLISHED".equals(existing.getStatus())) {
            campusEventMapper.update(event, new LambdaQueryWrapper<CampusEvent>()
                    .eq(CampusEvent::getDedupKey, dedupKey));
            insertSourceRefIfMissing(existing.getId(), source, item);
            return true;
        }
        return insertSourceRefIfMissing(existing.getId(), source, item);
    }

    private boolean insertSourceRefIfMissing(Long eventId, DataSource source, WebCrawlItem item) {
        String rawDocId = "web_crawl_item:" + item.getId();
        Long existing = eventSourceRefMapper.selectCount(new LambdaQueryWrapper<EventSourceRef>()
                .eq(EventSourceRef::getRawDocId, rawDocId));
        if (existing != null && existing > 0) {
            return false;
        }
        EventSourceRef ref = new EventSourceRef();
        ref.setEventId(eventId);
        ref.setSourceId(source.getId());
        ref.setRawDocId(rawDocId);
        ref.setSourceUrl(item.getItemUrl());
        ref.setSourceTitle(item.getTitle());
        ref.setContentHash(firstNonBlank(item.getDetailContentHash(), item.getContentHash()));
        eventSourceRefMapper.insert(ref);
        return true;
    }

    private String classifyEventType(WebCrawlItem item) {
        String text = (item.getTitle() + " " + item.getDetailContent()).toLowerCase();
        if (text.contains("讲座") || text.contains("报告")) {
            return "LECTURE";
        }
        if (text.contains("考试") || text.contains("考场")) {
            return "EXAM";
        }
        if (text.contains("竞赛") || text.contains("比赛") || text.contains("创新创业")) {
            return "COMPETITION";
        }
        if (text.contains("课程") || text.contains("调课") || text.contains("教学")) {
            return "COURSE";
        }
        if (text.contains("服务") || text.contains("开放") || text.contains("维护")) {
            return "SERVICE";
        }
        return "NOTICE";
    }

    private String eventTypeTag(String eventType) {
        return switch (eventType) {
            case "LECTURE" -> "讲座";
            case "EXAM" -> "考试";
            case "COMPETITION" -> "竞赛";
            case "COURSE" -> "课程";
            case "SERVICE" -> "服务";
            default -> "通知";
        };
    }

    private LocalDateTime parseEventDate(String dateText) {
        if (!StringUtils.hasText(dateText)) {
            return null;
        }
        String normalized = dateText.trim();
        Matcher full = FULL_DATE.matcher(normalized);
        if (full.find()) {
            return toStartOfDay(full.group(1) + "-" + full.group(2) + "-" + full.group(3));
        }
        Matcher monthDay = MONTH_DAY.matcher(normalized);
        if (monthDay.find()) {
            return toStartOfDay(LocalDate.now().getYear() + "-" + monthDay.group(1) + "-" + monthDay.group(2));
        }
        return null;
    }

    private LocalDateTime toStartOfDay(String value) {
        try {
            return LocalDate.parse(value, EVENT_DATE).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    private CrawlTask newTask(DataSource source) {
        CrawlTask task = new CrawlTask();
        task.setSourceId(source.getId());
        task.setTaskStatus("RUNNING");
        task.setCrawlUrl(source.getBaseUrl());
        task.setStartedAt(LocalDateTime.now());
        return task;
    }

    private boolean isSupported(DataSource source) {
        return source.getEnabled() != null
                && source.getEnabled() == 1
                && PUBLIC_WEB.equals(source.getSourceType())
                && WEBMAGIC.equals(source.getParserType());
    }

    private boolean intervalTooSmall(DataSource source) {
        Integer seconds = source.getCrawlIntervalSeconds();
        return seconds == null || seconds <= crawlerProperties.getMinIntervalSeconds();
    }

    private CrawlSourceResult finish(CrawlTask task, DataSource source, String status, Integer httpStatus,
                                     String parserVersion, int discoveredCount, int persistedCount,
                                     List<CrawledLink> links, String failReason,
                                     String storedFailReason) {
        task.setTaskStatus(status);
        task.setHttpStatus(httpStatus);
        task.setFailReason(truncate(storedFailReason, MAX_FAIL_REASON_LENGTH));
        task.setFinishedAt(LocalDateTime.now());
        crawlTaskMapper.updateById(task);
        return new CrawlSourceResult(task.getId(), source.getId(), source.getName(), status, httpStatus,
                source.getBaseUrl(), discoveredCount, persistedCount, links, parserVersion, failReason,
                task.getStartedAt(), task.getFinishedAt());
    }

    private CrawlItemResponse toItemResponse(WebCrawlItem item) {
        return new CrawlItemResponse(item.getId(), item.getSourceId(), item.getSourceName(), item.getSourceUrl(),
                item.getItemUrl(), item.getTitle(), item.getDetailTitle(), item.getDateText(), item.getSummary(),
                item.getDetailContent(), item.getParseStatus(), item.getParseError(), item.getDetailHttpStatus(),
                item.getFetchedAt(), item.getDetailFetchedAt());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String tagsJson(String first, String second) {
        return "[\"" + jsonEscape(first) + "\",\"" + jsonEscape(second) + "\"]";
    }

    private String jsonEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record ListPageCrawl(
            String parserVersion,
            List<CrawledLink> links,
            Integer httpStatus,
            String etag,
            String lastModified
    ) {
    }
}
