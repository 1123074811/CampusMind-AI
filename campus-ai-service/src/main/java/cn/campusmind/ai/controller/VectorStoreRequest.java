package cn.campusmind.ai.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 向量入库请求。{@code event} 复用 {@link EventVectorTextRequest} 的字段与校验，
 * 服务端会先构建向量文本再入库；{@code docId} 可选，缺省时由向量库生成。
 * {@code visibility} 和 {@code ownerUserId} 用于向量库可见性过滤。
 */
public record VectorStoreRequest(
        String docId,

        String visibility,

        Long ownerUserId,

        @Valid
        @NotNull
        EventVectorTextRequest event
) {
}
