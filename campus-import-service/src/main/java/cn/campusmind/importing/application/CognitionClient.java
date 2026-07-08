package cn.campusmind.importing.application;

/**
 * 认知抽取客户端：调用 campus-ai-service 将非结构化文本转为结构化事件候选。
 */
public interface CognitionClient {

    CognitionResult extract(String sourceType, String plainText);
}
