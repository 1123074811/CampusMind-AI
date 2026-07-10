package cn.campusmind.crawler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.crawler")
public class CrawlerProperties {

    private int defaultIntervalSeconds = 5;
    private int minIntervalSeconds = 3;
    private int robotsCacheTtlHours = 12;
    private int maxRetry = 3;
    private String dailyCron = "0 30 6 * * *";
    private String userAgent = "CampusEventBot/1.0 (+contact: admin@example.edu)";

    public int getDefaultIntervalSeconds() {
        return defaultIntervalSeconds;
    }

    public void setDefaultIntervalSeconds(int defaultIntervalSeconds) {
        this.defaultIntervalSeconds = defaultIntervalSeconds;
    }

    public int getMinIntervalSeconds() {
        return minIntervalSeconds;
    }

    public void setMinIntervalSeconds(int minIntervalSeconds) {
        this.minIntervalSeconds = minIntervalSeconds;
    }

    public int getRobotsCacheTtlHours() {
        return robotsCacheTtlHours;
    }

    public void setRobotsCacheTtlHours(int robotsCacheTtlHours) {
        this.robotsCacheTtlHours = robotsCacheTtlHours;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    public String getDailyCron() {
        return dailyCron;
    }

    public void setDailyCron(String dailyCron) {
        this.dailyCron = dailyCron;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
