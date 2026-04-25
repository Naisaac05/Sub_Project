package com.devmatch.dto.community;

import com.devmatch.entity.Post;
import com.devmatch.util.CommunityCategoryNormalizer;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PostResponse {

    private Long id;
    private Long authorId;
    private String authorName;
    private String category;
    private String title;
    private String content;
    private String imageUrl;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Boolean liked;
    private LocalDateTime createdAt;

    public static PostResponse from(Post post, boolean liked) {
        return new PostResponse(
                post.getId(),
                post.getAuthor().getId(),
                post.getAuthor().getName(),
                CommunityCategoryNormalizer.normalize(post.getCategory()),
                post.getTitle(),
                post.getContent(),
                post.getImageUrl(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getCommentCount(),
                liked,
                post.getCreatedAt()
        );
    }
}
