package com.example.drama;

public final class SocialDto {
    private SocialDto() {}

    public record Response(Long dramaId, long likeCount, long commentCount, long favoriteCount,
                           boolean liked, boolean favorited) {}
}
