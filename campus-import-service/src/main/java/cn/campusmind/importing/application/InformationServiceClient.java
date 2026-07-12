package cn.campusmind.importing.application;

/**
 * 信息服务客户端：通过 HTTP 调用 campus-feed-service 创建 information_item。
 */
public interface InformationServiceClient {

    /**
     * 幂等创建信息条目，返回信息条目 ID。
     */
    Long createItem(String title, String detailContent, String sourceName,
                    String sourceUrl, String itemUrl, String contentHash);
}
