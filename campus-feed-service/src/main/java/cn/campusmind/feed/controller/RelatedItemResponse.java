package cn.campusmind.feed.controller;

public record RelatedItemResponse(
        Long id,
        String title,
        String sourceName,
        String displayTime,
        String fuseNote
) {
}
