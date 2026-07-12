package cn.campusmind.crawler.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.crawler.config.CrawlerProperties;
import cn.campusmind.crawler.controller.CrawlItemResponse;
import cn.campusmind.crawler.domain.CrawlTask;
import cn.campusmind.crawler.domain.DataSource;
import cn.campusmind.crawler.domain.InformationItem;
import cn.campusmind.crawler.domain.WebCrawlItem;
import cn.campusmind.crawler.infrastructure.mapper.CrawlTaskMapper;
import cn.campusmind.crawler.infrastructure.mapper.DataSourceMapper;
import cn.campusmind.crawler.infrastructure.mapper.InformationItemMapper;
import cn.campusmind.crawler.infrastructure.mapper.WebCrawlItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CrawlerService {

    private static final String PUBLIC_WEB = "PUBLIC_WEB";
    private static final String WEBMAGIC = "WEBMAGIC";
    private static final Pattern DATE_TIME = Pattern.compile("(\\d{4})[年/.-](\\d{1,2})[月/.-](\\d{1,2})日?\\s+(\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?");
    private static final Pattern FULL_DATE = Pattern.compile("(\\d{4})[年/.-](\\d{1,2})[月/.-](\\d{1,2})");
    private static final Pattern MONTH_DAY = Pattern.compile("(?<!\\d)(\\d{1,2})[月/.-](\\d{1,2})");
    private static final DateTimeFormatter EVENT_DATE = DateTimeFormatter.ofPattern("yyyy-M-d");
    private static final int MAX_FAIL_REASON_LENGTH = 1000;

    private final DataSourceMapper dataSourceMapper;
    private final CrawlTaskMapper crawlTaskMapper;
    private final WebCrawlItemMapper webCrawlItemMapper;
    private final InformationItemMapper informationItemMapper;
    private final SelectorConfigParser selectorConfigParser;
    private final ListPageParser listPageParser;
    private final DetailPageParser detailPageParser;
    private final PublicWebFetcher publicWebFetcher;
    private final CrawlerProperties crawlerProperties;
    private final JdbcTemplate jdbcTemplate;
    private final AiCardExtractor aiCardExtractor;
    private final VectorPusher vectorPusher;

    public CrawlerService(DataSourceMapper dataSourceMapper,
                          CrawlTaskMapper crawlTaskMapper,
                          WebCrawlItemMapper webCrawlItemMapper,
                          InformationItemMapper informationItemMapper,
                          SelectorConfigParser selectorConfigParser,
                          ListPageParser listPageParser,
                          DetailPageParser detailPageParser,
                          PublicWebFetcher publicWebFetcher,
                          CrawlerProperties crawlerProperties,
                          JdbcTemplate jdbcTemplate,
                          AiCardExtractor aiCardExtractor,
                          VectorPusher vectorPusher) {
        this.dataSourceMapper = dataSourceMapper;
        this.crawlTaskMapper = crawlTaskMapper;
        this.webCrawlItemMapper = webCrawlItemMapper;
        this.informationItemMapper = informationItemMapper;
        this.selectorConfigParser = selectorConfigParser;
        this.listPageParser = listPageParser;
        this.detailPageParser = detailPageParser;
        this.publicWebFetcher = publicWebFetcher;
        this.crawlerProperties = crawlerProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.aiCardExtractor = aiCardExtractor;
        this.vectorPusher = vectorPusher;
    }

    /**
     * 一次性回填：将 ai_status=SUCCESS 的历史条目推送到向量库，用于服务重启后恢复 RAG 检索。
     */
    public int backfillVectorStore(int size) {
        int batchSize = Math.min(Math.max(size, 1), 500);
        List<InformationItem> items = informationItemMapper.selectList(new LambdaQueryWrapper<InformationItem>()
                .eq(InformationItem::getAiStatus, "SUCCESS")
                .orderByDesc(InformationItem::getPublishTime)
                .last("LIMIT " + batchSize));
        int pushed = 0;
        for (InformationItem item : items) {
            if (item.getTitle() == null || item.getTitle().isBlank()) continue;
            vectorPusher.push(item.getId(), item.getTitle(), item.getAiEventType(),
                    item.getAiSummary(), item.getPublishTime(), item.getDetailContent());
            pushed++;
        }
        return pushed;
    }

    @Transactional
    public CrawlSourceResult crawlSource(Long sourceId) {
        return crawlSource(sourceId, new CrawlOptions(365, null, "MANUAL"));
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
            if (source != null && upsertInformationItem(source, item)) {
                publishedCount++;
            }
        }
        return publishedCount;
    }

    public int processPendingAiCards(int size) {
        int batchSize = Math.min(Math.max(size, 1), 200);
        LocalDateTime retryBefore = LocalDateTime.now().minusMinutes(30);
        List<InformationItem> items = informationItemMapper.selectList(new LambdaQueryWrapper<InformationItem>()
                .eq(InformationItem::getParseStatus, "DETAIL_SUCCESS")
                .and(status -> status.eq(InformationItem::getAiStatus, "PENDING")
                        .or().isNull(InformationItem::getAiStatus)
                        .or(failed -> failed.eq(InformationItem::getAiStatus, "FAILED")
                                .lt(InformationItem::getAiProcessedAt, retryBefore)))
                .orderByAsc(InformationItem::getFetchedAt)
                .last("LIMIT " + batchSize));
        items.forEach(this::extractAiCard);
        return items.size();
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
                    "仅支持启用的 PUBLIC_WEB/WEBMAGIC 数据源", options.normalizedTrigger());
        }
        if (intervalTooSmall(source)) {
            return finish(task, source, "SKIPPED", null, null, 0, 0, List.of(), null,
                    "采集间隔必须大于 " + crawlerProperties.getMinIntervalSeconds() + " 秒", options.normalizedTrigger());
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
                    pageCrawl.links().size(), persistedCount, filteredLinks, null, null, options.normalizedTrigger());
        } catch (Exception e) {
            return finish(task, source, "FAILED", task.getHttpStatus(), null, 0, 0, List.of(), e.getMessage(), e.getMessage(), options.normalizedTrigger());
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
        Map<String, CrawledLink> discovered = new LinkedHashMap<>();
        String nextUrl = source.getBaseUrl();
        String parserVersion = selectorConfig.getParserVersion();
        Integer firstHttpStatus = null;
        String firstEtag = null;
        String firstLastModified = null;
        int page = 1;
        Set<String> visitedPages = new LinkedHashSet<>();
        LocalDate cutoff = LocalDate.now().minusDays(options.normalizedDays() - 1L);
        while (StringUtils.hasText(nextUrl)) {
            if (!visitedPages.add(nextUrl)) {
                break;
            }
            PublicWebFetcher.FetchResult fetchResult = publicWebFetcher.fetch(nextUrl);
            if (page == 1) {
                firstHttpStatus = fetchResult.httpStatus();
                firstEtag = fetchResult.etag();
                firstLastModified = fetchResult.lastModified();
            }
            ParsedListPage parsed = listPageParser.parse(fetchResult.body(), nextUrl, selectorConfig);
            parserVersion = parsed.parserVersion();
            boolean reachedOldPage = !parsed.links().isEmpty() && parsed.links().stream()
                    .allMatch(link -> link.publishedDate() != null && link.publishedDate().isBefore(cutoff));
            int before = discovered.size();
            for (CrawledLink link : parsed.links()) {
                discovered.putIfAbsent(link.url(), link);
            }
            if (reachedOldPage) {
                break;
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
                upsertInformationItem(source, item);
                persistedCount++;
            } else if (contentChanged(existing, item)) {
                item.setId(existing.getId());
                webCrawlItemMapper.updateById(item);
                upsertInformationItem(source, item);
                persistedCount++;
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
            if (StringUtils.hasText(detail.publishedAtText())) {
                item.setDateText(detail.publishedAtText());
            }
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

    private boolean upsertInformationItem(DataSource source, WebCrawlItem item) {
        if (!"DETAIL_SUCCESS".equals(item.getParseStatus()) || !StringUtils.hasText(item.getDetailContent())) {
            return false;
        }
        String title = truncate(firstNonBlank(item.getDetailTitle(), item.getTitle()), 512);
        String contentHash = firstNonBlank(item.getDetailContentHash(), sha256(item.getDetailContent()));
        InformationItem existing = informationItemMapper.selectOne(new LambdaQueryWrapper<InformationItem>()
                .eq(InformationItem::getItemUrl, item.getItemUrl())
                .eq(InformationItem::getTitle, title)
                .last("LIMIT 1"));

        InformationItem informationItem = new InformationItem();
        informationItem.setSourceId(source.getId());
        informationItem.setSourceName(source.getName());
        informationItem.setSourceUrl(source.getBaseUrl());
        informationItem.setItemUrl(item.getItemUrl());
        informationItem.setTitle(title);
        informationItem.setPublishTime(parseEventDate(item.getDateText()));
        informationItem.setFetchedAt(item.getFetchedAt() == null ? LocalDateTime.now() : item.getFetchedAt());
        informationItem.setDetailContent(item.getDetailContent());
        informationItem.setContentHash(contentHash);
        informationItem.setParseStatus(item.getParseStatus());
        informationItem.setParseError(item.getParseError());

        if (existing == null) {
            informationItem.setItemStatus("ACTIVE");
            informationItem.setAiStatus("PENDING");
            informationItemMapper.insert(informationItem);
        } else {
            informationItem.setId(existing.getId());
            boolean changed = !Objects.equals(existing.getContentHash(), contentHash);
            informationItem.setItemStatus(changed ? "UPDATED" : "ACTIVE");
            if (changed) {
                informationItem.setAiStatus("PENDING");
                informationItem.setAiEventType("OTHER");
                informationItem.setAiSummary("");
                informationItem.setAiCardJson("{}");
                informationItem.setAiNeedReview(false);
            }
            informationItemMapper.updateById(informationItem);
        }
        return true;
    }

    private void extractAiCard(InformationItem item) {
        try {
            AiCardExtractor.Result result = aiCardExtractor.extract(item.getId(), item.getItemUrl(), item.getDetailContent());
            item.setAiStatus(result.needHumanReview() ? "REVIEW" : "SUCCESS");
            item.setAiEventType(result.eventType());
            item.setAiSummary(result.summary());
            item.setAiCardJson(result.cardJson());
            item.setAiNeedReview(result.needHumanReview());
            item.setAiError(null);
            // 推送事件到 AI 向量库，供 RAG 检索
            vectorPusher.push(item.getId(), item.getTitle(), result.eventType(),
                    result.summary(), item.getPublishTime(), item.getDetailContent());
        } catch (Exception ex) {
            item.setAiStatus("FAILED");
            item.setAiError(truncate(ex.getMessage(), MAX_FAIL_REASON_LENGTH));
        }
        item.setAiProcessedAt(LocalDateTime.now());
        informationItemMapper.updateById(item);
    }

    private LocalDateTime parseEventDate(String dateText) {
        if (!StringUtils.hasText(dateText)) {
            return null;
        }
        String normalized = dateText.trim();
        Matcher dateTime = DATE_TIME.matcher(normalized);
        if (dateTime.find()) {
            return toDateTime(dateTime);
        }
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

    private LocalDateTime toDateTime(Matcher matcher) {
        int second = matcher.group(6) == null ? 0 : Integer.parseInt(matcher.group(6));
        try {
            return LocalDateTime.of(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    Integer.parseInt(matcher.group(4)),
                    Integer.parseInt(matcher.group(5)),
                    second
            );
        } catch (RuntimeException ignored) {
            return null;
        }
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
                                     String storedFailReason, String trigger) {
        task.setTaskStatus(status);
        task.setHttpStatus(httpStatus);
        task.setFailReason(truncate(storedFailReason, MAX_FAIL_REASON_LENGTH));
        task.setFinishedAt(LocalDateTime.now());
        crawlTaskMapper.updateById(task);
        writeCrawlLog(source, task, status, discoveredCount, persistedCount, trigger);
        return new CrawlSourceResult(task.getId(), source.getId(), source.getName(), status, httpStatus,
                source.getBaseUrl(), discoveredCount, persistedCount, links, parserVersion, failReason,
                task.getStartedAt(), task.getFinishedAt());
    }

    private void writeCrawlLog(DataSource source, CrawlTask task, String status, int discoveredCount, int persistedCount, String trigger) {
        String action = "AUTO".equalsIgnoreCase(trigger) ? "AUTO_CRAWL" : "MANUAL_CRAWL";
        String comment = source.getName() + "：" + status + "，发现 " + discoveredCount + " 条，入库 " + persistedCount + " 条";
        jdbcTemplate.update("""
                INSERT INTO event_audit_log (event_id, operator_id, action, before_snapshot, after_snapshot, comment)
                VALUES (NULL, NULL, ?, NULL, JSON_OBJECT('taskId', ?, 'sourceId', ?, 'status', ?), ?)
                """, action, task.getId(), source.getId(), status, truncate(comment, 512));
    }

    private CrawlItemResponse toItemResponse(WebCrawlItem item) {
        return new CrawlItemResponse(item.getId(), item.getSourceId(), item.getSourceName(), item.getSourceUrl(),
                item.getItemUrl(), item.getTitle(), item.getDetailTitle(), item.getDateText(), item.getSummary(),
                item.getDetailContent(), item.getParseStatus(), item.getParseError(), item.getDetailHttpStatus(),
                item.getFetchedAt(), item.getDetailFetchedAt(), favoriteCount(item.getItemUrl()));
    }

    private long favoriteCount(String itemUrl) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM user_information_state state
                JOIN information_item item ON item.id = state.item_id
                WHERE item.item_url = ? AND state.favorited_at IS NOT NULL
                """, Long.class, itemUrl);
        return count == null ? 0 : count;
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

    private record ListPageCrawl(
            String parserVersion,
            List<CrawledLink> links,
            Integer httpStatus,
            String etag,
            String lastModified
    ) {
    }
}
