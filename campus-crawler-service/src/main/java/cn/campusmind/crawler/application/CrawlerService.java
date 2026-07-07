package cn.campusmind.crawler.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.crawler.config.CrawlerProperties;
import cn.campusmind.crawler.domain.CrawlTask;
import cn.campusmind.crawler.domain.DataSource;
import cn.campusmind.crawler.domain.WebCrawlItem;
import cn.campusmind.crawler.infrastructure.mapper.CrawlTaskMapper;
import cn.campusmind.crawler.infrastructure.mapper.DataSourceMapper;
import cn.campusmind.crawler.infrastructure.mapper.WebCrawlItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class CrawlerService {

    private static final String PUBLIC_WEB = "PUBLIC_WEB";
    private static final String WEBMAGIC = "WEBMAGIC";

    private final DataSourceMapper dataSourceMapper;
    private final CrawlTaskMapper crawlTaskMapper;
    private final WebCrawlItemMapper webCrawlItemMapper;
    private final SelectorConfigParser selectorConfigParser;
    private final ListPageParser listPageParser;
    private final PublicWebFetcher publicWebFetcher;
    private final CrawlerProperties crawlerProperties;

    public CrawlerService(DataSourceMapper dataSourceMapper,
                          CrawlTaskMapper crawlTaskMapper,
                          WebCrawlItemMapper webCrawlItemMapper,
                          SelectorConfigParser selectorConfigParser,
                          ListPageParser listPageParser,
                          PublicWebFetcher publicWebFetcher,
                          CrawlerProperties crawlerProperties) {
        this.dataSourceMapper = dataSourceMapper;
        this.crawlTaskMapper = crawlTaskMapper;
        this.webCrawlItemMapper = webCrawlItemMapper;
        this.selectorConfigParser = selectorConfigParser;
        this.listPageParser = listPageParser;
        this.publicWebFetcher = publicWebFetcher;
        this.crawlerProperties = crawlerProperties;
    }

    @Transactional
    public CrawlSourceResult crawlSource(Long sourceId) {
        DataSource source = dataSourceMapper.selectById(sourceId);
        if (source == null) {
            throw new BusinessException("SOURCE_NOT_FOUND", "数据源不存在", HttpStatus.NOT_FOUND);
        }
        CrawlTask task = newTask(source);
        crawlTaskMapper.insert(task);
        if (!isSupported(source)) {
            return finish(task, source, "SKIPPED", null, null, List.of(), null,
                    "仅支持启用的 PUBLIC_WEB/WEBMAGIC 数据源");
        }
        if (intervalTooSmall(source)) {
            return finish(task, source, "SKIPPED", null, null, List.of(), null,
                    "采集间隔必须大于 " + crawlerProperties.getMinIntervalSeconds() + " 秒");
        }
        try {
            SelectorConfig selectorConfig = selectorConfigParser.parse(source.getSelectorConfig());
            PublicWebFetcher.FetchResult fetchResult = publicWebFetcher.fetch(source.getBaseUrl());
            ParsedListPage parsed = listPageParser.parse(fetchResult.body(), source.getBaseUrl(), selectorConfig);
            persistItems(task, source, parsed);
            task.setHttpStatus(fetchResult.httpStatus());
            task.setEtag(fetchResult.etag());
            task.setLastModified(fetchResult.lastModified());
            source.setLastCrawledAt(LocalDateTime.now());
            dataSourceMapper.updateById(source);
            return finish(task, source, "SUCCESS", fetchResult.httpStatus(), parsed.parserVersion(),
                    parsed.links(), null, null);
        } catch (Exception e) {
            return finish(task, source, "FAILED", task.getHttpStatus(), null, List.of(), e.getMessage(), e.getMessage());
        }
    }

    private void persistItems(CrawlTask task, DataSource source, ParsedListPage parsed) {
        LocalDateTime fetchedAt = LocalDateTime.now();
        for (CrawledLink link : parsed.links()) {
            String hash = sha256(source.getId() + "|" + link.title() + "|" + link.url() + "|"
                    + link.dateText() + "|" + link.summary());
            Long existing = webCrawlItemMapper.selectCount(new LambdaQueryWrapper<WebCrawlItem>()
                    .eq(WebCrawlItem::getContentHash, hash));
            if (existing != null && existing > 0) {
                continue;
            }
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
            item.setParserVersion(parsed.parserVersion());
            item.setFetchedAt(fetchedAt);
            webCrawlItemMapper.insert(item);
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
                                     String parserVersion, List<CrawledLink> links, String failReason,
                                     String storedFailReason) {
        task.setTaskStatus(status);
        task.setHttpStatus(httpStatus);
        task.setFailReason(storedFailReason);
        task.setFinishedAt(LocalDateTime.now());
        crawlTaskMapper.updateById(task);
        return new CrawlSourceResult(task.getId(), source.getId(), source.getName(), status, httpStatus,
                source.getBaseUrl(), links.size(), links, parserVersion, failReason,
                task.getStartedAt(), task.getFinishedAt());
    }
}
