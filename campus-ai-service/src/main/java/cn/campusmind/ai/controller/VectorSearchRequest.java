package cn.campusmind.ai.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 向量检索请求。
 *
 * @param query 查询文本
 * @param topK  召回数量，缺省或 <=0 时取 10
 */
public record VectorSearchRequest(
        @NotBlank
        @Size(max = 1000)
        String query,

        Integer topK
) {
}
