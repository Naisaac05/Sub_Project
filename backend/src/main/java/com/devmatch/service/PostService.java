package com.devmatch.service;

import com.devmatch.dto.community.CommentCreateRequest;
import com.devmatch.dto.community.CommentResponse;
import com.devmatch.dto.community.PostCreateRequest;
import com.devmatch.dto.community.PostResponse;
import com.devmatch.entity.Comment;
import com.devmatch.entity.Post;
import com.devmatch.entity.PostLike;
import com.devmatch.entity.User;
import com.devmatch.exception.CommentNotFoundException;
import com.devmatch.exception.PostNotFoundException;
import com.devmatch.exception.UnauthorizedPostException;
import com.devmatch.exception.UserNotFoundException;
import com.devmatch.repository.CommentRepository;
import com.devmatch.repository.PostLikeRepository;
import com.devmatch.repository.PostRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;

    // ===== 게시글 =====

    @Transactional
    public PostResponse createPost(Long userId, PostCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다"));

        Post post = Post.builder()
                .author(user)
                .title(request.getTitle())
                .content(request.getContent())
                .build();

        post = postRepository.save(post);
        return PostResponse.from(post, false);
    }

    public Page<PostResponse> getPosts(Long userId, Pageable pageable) {
        return postRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(post -> {
                    boolean liked = postLikeRepository.existsByPostIdAndUserId(post.getId(), userId);
                    return PostResponse.from(post, liked);
                });
    }

    public PostResponse getPost(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("게시글을 찾을 수 없습니다"));

        boolean liked = postLikeRepository.existsByPostIdAndUserId(postId, userId);
        return PostResponse.from(post, liked);
    }

    @Transactional
    public PostResponse updatePost(Long userId, Long postId, PostCreateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("게시글을 찾을 수 없습니다"));

        if (!post.getAuthor().getId().equals(userId)) {
            throw new UnauthorizedPostException("본인의 게시글만 수정할 수 있습니다");
        }

        post.update(request.getTitle(), request.getContent());

        boolean liked = postLikeRepository.existsByPostIdAndUserId(postId, userId);
        return PostResponse.from(post, liked);
    }

    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("게시글을 찾을 수 없습니다"));

        if (!post.getAuthor().getId().equals(userId)) {
            throw new UnauthorizedPostException("본인의 게시글만 삭제할 수 있습니다");
        }

        postRepository.delete(post);
    }

    // ===== 좋아요 =====

    @Transactional
    public PostResponse toggleLike(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("게시글을 찾을 수 없습니다"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다"));

        Optional<PostLike> existingLike = postLikeRepository.findByPostIdAndUserId(postId, userId);

        boolean liked;
        if (existingLike.isPresent()) {
            postLikeRepository.delete(existingLike.get());
            post.decrementLikeCount();
            liked = false;
        } else {
            PostLike postLike = PostLike.builder()
                    .post(post)
                    .user(user)
                    .build();
            postLikeRepository.save(postLike);
            post.incrementLikeCount();
            liked = true;
        }

        return PostResponse.from(post, liked);
    }

    // ===== 댓글 =====

    @Transactional
    public CommentResponse createComment(Long userId, Long postId, CommentCreateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("게시글을 찾을 수 없습니다"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다"));

        Comment comment = Comment.builder()
                .post(post)
                .author(user)
                .content(request.getContent())
                .build();

        comment = commentRepository.save(comment);
        post.incrementCommentCount();

        return CommentResponse.from(comment);
    }

    public List<CommentResponse> getComments(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new PostNotFoundException("게시글을 찾을 수 없습니다");
        }

        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(CommentResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteComment(Long userId, Long postId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException("댓글을 찾을 수 없습니다"));

        if (!comment.getPost().getId().equals(postId)) {
            throw new CommentNotFoundException("해당 게시글의 댓글이 아닙니다");
        }

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new UnauthorizedPostException("본인의 댓글만 삭제할 수 있습니다");
        }

        commentRepository.delete(comment);
    }
}
