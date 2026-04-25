package com.devmatch.service;

import com.devmatch.dto.community.CommentCreateRequest;
import com.devmatch.dto.community.CommentResponse;
import com.devmatch.dto.community.ImageUploadResponse;
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
import com.devmatch.util.CommunityCategoryNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Transactional
    public PostResponse createPost(Long userId, PostCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        Post post = Post.builder()
                .author(user)
                .category(CommunityCategoryNormalizer.normalize(request.getCategory()))
                .title(request.getTitle())
                .content(request.getContent())
                .imageUrl(request.getImageUrl())
                .build();

        return PostResponse.from(postRepository.save(post), false);
    }

    public Page<PostResponse> getPosts(Long userId, Pageable pageable) {
        return postRepository.findByDeletedFalseOrderByCreatedAtDesc(pageable)
                .map(post -> PostResponse.from(
                        post,
                        postLikeRepository.existsByPostIdAndUserId(post.getId(), userId)
                ));
    }

    @Transactional
    public PostResponse getPost(Long userId, Long postId) {
        Post post = findActivePost(postId);

        post.incrementViewCount();
        return PostResponse.from(post, postLikeRepository.existsByPostIdAndUserId(postId, userId));
    }

    @Transactional
    public PostResponse updatePost(Long userId, Long postId, PostCreateRequest request) {
        Post post = findActivePost(postId);

        if (!post.getAuthor().getId().equals(userId)) {
            throw new UnauthorizedPostException("본인이 작성한 게시글만 수정할 수 있습니다.");
        }

        post.update(
                request.getTitle(),
                request.getContent(),
                CommunityCategoryNormalizer.normalize(request.getCategory()),
                request.getImageUrl()
        );
        return PostResponse.from(post, postLikeRepository.existsByPostIdAndUserId(postId, userId));
    }

    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = findActivePost(postId);

        if (!post.getAuthor().getId().equals(userId)) {
            throw new UnauthorizedPostException("본인이 작성한 게시글만 삭제할 수 있습니다.");
        }

        commentRepository.deleteByPostId(postId);
        postLikeRepository.deleteByPostId(postId);
        postRepository.delete(post);
    }

    @Transactional
    public ImageUploadResponse uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 이미지 파일을 선택해주세요.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        String originalFilename = file.getOriginalFilename();
        String storedName = UUID.randomUUID() + extractExtension(originalFilename);
        Path uploadPath = Paths.get(uploadDir, "community");

        try {
            Files.createDirectories(uploadPath);
            Files.copy(file.getInputStream(), uploadPath.resolve(storedName));
        } catch (IOException e) {
            throw new RuntimeException("커뮤니티 이미지 업로드에 실패했습니다.");
        }

        return new ImageUploadResponse("/uploads/community/" + storedName);
    }

    @Transactional
    public PostResponse toggleLike(Long userId, Long postId) {
        Post post = findActivePost(postId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        Optional<PostLike> existingLike = postLikeRepository.findByPostIdAndUserId(postId, userId);

        boolean liked;
        if (existingLike.isPresent()) {
            postLikeRepository.delete(existingLike.get());
            post.decrementLikeCount();
            liked = false;
        } else {
            postLikeRepository.save(PostLike.builder().post(post).user(user).build());
            post.incrementLikeCount();
            liked = true;
        }

        return PostResponse.from(post, liked);
    }

    @Transactional
    public CommentResponse createComment(Long userId, Long postId, CommentCreateRequest request) {
        Post post = findActivePost(postId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        Comment comment = Comment.builder()
                .post(post)
                .author(user)
                .content(request.getContent())
                .build();

        post.incrementCommentCount();
        return CommentResponse.from(commentRepository.save(comment));
    }

    public List<CommentResponse> getComments(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new PostNotFoundException("게시글을 찾을 수 없습니다.");
        }

        return commentRepository.findByPostIdAndDeletedFalseOrderByCreatedAtAsc(postId).stream()
                .map(CommentResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteComment(Long userId, Long postId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException("댓글을 찾을 수 없습니다."));

        if (!comment.getPost().getId().equals(postId)) {
            throw new CommentNotFoundException("해당 게시글의 댓글이 아닙니다.");
        }

        if (comment.isDeleted()) {
            throw new CommentNotFoundException("댓글을 찾을 수 없습니다.");
        }

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new UnauthorizedPostException("본인이 작성한 댓글만 삭제할 수 있습니다.");
        }

        commentRepository.delete(comment);
        comment.getPost().decrementCommentCount();
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return ".png";
        }

        int extensionStart = originalFilename.lastIndexOf('.');
        if (extensionStart < 0) {
            return ".png";
        }

        return originalFilename.substring(extensionStart);
    }

    private Post findActivePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("게시글을 찾을 수 없습니다."));
        if (post.isDeleted()) {
            throw new PostNotFoundException("게시글을 찾을 수 없습니다.");
        }
        return post;
    }
}
