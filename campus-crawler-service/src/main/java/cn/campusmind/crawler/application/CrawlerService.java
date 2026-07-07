package cn.campusmind.crawler.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.crawler.config.CrawlerProperties;
import cn.campusmind.crawler.domain.CrawlTask;
import cn.campusmind.crawler.domain.DataSource;
import cn.campusmind.crawler.infrastructure.mapper.CrawlTaskMapper;
import cn.campusmind.crawler.infrastructure.mapper.DataSourceMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CrawlerService {

    private static final String PUBLIC_WEB = "PUBLIC_WEB";
    private static final String WEBMAGIC = "WEBMAGIC";

    private final DataSourceMapper dataSourceMapper;
    private final CrawlTaskMapper crawlTaskMapper;
    private final SelectorConfigParser selectorConfigParser;
    private final ListPageParser listPageParser;
    private final PublicWebFetcher publicWebFetcher;
    private final CrawlerProperties crawlerProperties;

    public CrawlerService(DataSourceMapper dataSourceMapper,
                          CrawlTaskMapper crawlTaskMapper,
                          SelectorConfigParser selectorConfigParser,
                          ListPageParser listPageParser,
                          PublicWebFetcher publicWebFetcher,
                          CrawlerProperties crawlerProperties) {
        this.dataSourceMapper = dataSourceMapper;
        this.crawlTaskMapper = crawlTaskMapper;
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
