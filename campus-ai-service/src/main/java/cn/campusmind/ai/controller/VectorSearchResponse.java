package cn.campusmind.ai.controller;

import cn.campusmind.ai.domain.VectorSearchHit;

import java.util.List;

/**
 * 向量检索响应。
 *
 * @param hits  按相关性降序的命中列表
 * @param total 命中总数
 */
public record VectorSearchResponse(
        List<VectorSearchHit> hits,
        int total
) {
}
