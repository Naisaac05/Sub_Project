'use client';

import { useEffect, useMemo, useState } from 'react';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import {
  COMMUNITY_CATEGORIES,
  type CommunityCategory,
  type CommunityComment,
  type CommunityPost,
  createCommunityComment,
  createCommunityPost,
  deleteCommunityComment,
  deleteCommunityPost,
  getCommunityComments,
  getCommunityPost,
  getCommunityPosts,
  toggleCommunityLike,
  updateCommunityPost,
} from '@/lib/community';
import {
  Search,
  MessageSquare,
  Heart,
  Eye,
  Clock,
  PenSquare,
  TrendingUp,
  Bookmark,
  X,
  Send,
  Trash2,
  Pencil,
} from 'lucide-react';

const FILTER_CATEGORIES = ['전체', ...COMMUNITY_CATEGORIES] as const;

function formatRelativeTime(value: string) {
  const target = new Date(value).getTime();
  const diffMinutes = Math.max(1, Math.floor((Date.now() - target) / 60000));

  if (diffMinutes < 60) return `${diffMinutes}분 전`;
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}시간 전`;
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 7) return `${diffDays}일 전`;
  return new Date(value).toLocaleDateString('ko-KR');
}

function formatCount(value: number) {
  if (value >= 1000) {
    return `${(value / 1000).toFixed(1)}k`;
  }
  return String(value);
}

type EditorState = {
  open: boolean;
  mode: 'create' | 'edit';
  targetId: number | null;
  category: CommunityCategory;
  title: string;
  content: string;
};

const initialEditor: EditorState = {
  open: false,
  mode: 'create',
  targetId: null,
  category: '질문/답변',
  title: '',
  content: '',
};

export default function CommunityPage() {
  const { user, isLoggedIn } = useAuth();
  const [selectedCategory, setSelectedCategory] = useState<(typeof FILTER_CATEGORIES)[number]>('전체');
  const [search, setSearch] = useState('');
  const [posts, setPosts] = useState<CommunityPost[]>([]);
  const [selectedPost, setSelectedPost] = useState<CommunityPost | null>(null);
  const [comments, setComments] = useState<CommunityComment[]>([]);
  const [commentDraft, setCommentDraft] = useState('');
  const [editor, setEditor] = useState<EditorState>(initialEditor);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const loadPosts = async () => {
    setLoading(true);
    try {
      const response = await getCommunityPosts();
      setPosts(response.data.content);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadPosts();
  }, []);

  const filteredPosts = useMemo(() => {
    return posts.filter((post) => {
      const matchesCategory = selectedCategory === '전체' || post.category === selectedCategory;
      const keyword = search.trim().toLowerCase();
      const matchesSearch =
        keyword.length === 0 ||
        post.title.toLowerCase().includes(keyword) ||
        post.content.toLowerCase().includes(keyword) ||
        post.authorName.toLowerCase().includes(keyword);
      return matchesCategory && matchesSearch;
    });
  }, [posts, search, selectedCategory]);

  const hotPosts = useMemo(() => {
    return [...posts]
      .sort((a, b) => b.likeCount + b.commentCount + b.viewCount - (a.likeCount + a.commentCount + a.viewCount))
      .slice(0, 4);
  }, [posts]);

  const popularTags = useMemo(() => {
    const tags = new Map<string, number>();
    posts.forEach((post) => {
      post.title
        .split(/[\s,/]+/)
        .map((token) => token.trim())
        .filter((token) => token.length >= 2)
        .slice(0, 4)
        .forEach((token) => tags.set(token, (tags.get(token) ?? 0) + 1));
    });

    return Array.from(tags.entries())
      .sort((a, b) => b[1] - a[1])
      .slice(0, 10)
      .map(([tag]) => tag);
  }, [posts]);

  const openDetail = async (postId: number) => {
    setDetailLoading(true);
    try {
      const [postResponse, commentResponse] = await Promise.all([
        getCommunityPost(postId),
        getCommunityComments(postId),
      ]);

      setSelectedPost(postResponse.data);
      setComments(commentResponse.data);
      setPosts((current) =>
        current.map((item) => (item.id === postResponse.data.id ? postResponse.data : item))
      );
    } catch (error) {
      console.error(error);
    } finally {
      setDetailLoading(false);
    }
  };

  const handleSubmitPost = async () => {
    if (!isLoggedIn) {
      alert('로그인 후 글을 작성할 수 있습니다.');
      return;
    }

    if (!editor.title.trim() || !editor.content.trim()) {
      alert('카테고리, 제목, 내용을 모두 입력해 주세요.');
      return;
    }

    setSubmitting(true);
    try {
      if (editor.mode === 'edit' && editor.targetId) {
        const response = await updateCommunityPost(editor.targetId, {
          category: editor.category,
          title: editor.title.trim(),
          content: editor.content.trim(),
        });
        const updated = response.data;
        setPosts((current) => current.map((post) => (post.id === updated.id ? updated : post)));
        setSelectedPost((current) => (current?.id === updated.id ? updated : current));
      } else {
        const response = await createCommunityPost({
          category: editor.category,
          title: editor.title.trim(),
          content: editor.content.trim(),
        });
        setPosts((current) => [response.data, ...current]);
      }

      setEditor(initialEditor);
    } catch (error) {
      console.error(error);
      alert('게시글 저장 중 오류가 발생했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleLike = async (postId: number) => {
    if (!isLoggedIn) {
      alert('로그인 후 좋아요를 누를 수 있습니다.');
      return;
    }

    try {
      const response = await toggleCommunityLike(postId);
      const updated = response.data;
      setPosts((current) => current.map((post) => (post.id === updated.id ? updated : post)));
      setSelectedPost((current) => (current?.id === updated.id ? updated : current));
    } catch (error) {
      console.error(error);
    }
  };

  const handleDeletePost = async (postId: number) => {
    if (!confirm('이 글을 삭제할까요?')) {
      return;
    }

    try {
      await deleteCommunityPost(postId);
      setPosts((current) => current.filter((post) => post.id !== postId));
      setSelectedPost((current) => (current?.id === postId ? null : current));
      setComments([]);
    } catch (error) {
      console.error(error);
      alert('게시글 삭제 중 오류가 발생했습니다.');
    }
  };

  const handleSubmitComment = async () => {
    if (!selectedPost || !commentDraft.trim()) {
      return;
    }

    if (!isLoggedIn) {
      alert('로그인 후 댓글을 작성할 수 있습니다.');
      return;
    }

    setSubmitting(true);
    try {
      const response = await createCommunityComment(selectedPost.id, commentDraft.trim());
      setComments((current) => [...current, response.data]);
      setCommentDraft('');
      await openDetail(selectedPost.id);
    } catch (error) {
      console.error(error);
      alert('댓글 등록 중 오류가 발생했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteComment = async (commentId: number) => {
    if (!selectedPost || !confirm('이 댓글을 삭제할까요?')) {
      return;
    }

    try {
      await deleteCommunityComment(selectedPost.id, commentId);
      setComments((current) => current.filter((comment) => comment.id !== commentId));
      await openDetail(selectedPost.id);
    } catch (error) {
      console.error(error);
      alert('댓글 삭제 중 오류가 발생했습니다.');
    }
  };

  const openCreateEditor = (category?: CommunityCategory) => {
    setEditor({
      ...initialEditor,
      open: true,
      category: category ?? (selectedCategory === '전체' ? '질문/답변' : selectedCategory),
    });
  };

  const openEditEditor = (post: CommunityPost) => {
    setEditor({
      open: true,
      mode: 'edit',
      targetId: post.id,
      category: post.category,
      title: post.title,
      content: post.content,
    });
  };

  return (
    <>
      <Header />
      <main className="min-h-screen bg-gray-50">
        <section className="relative overflow-hidden pt-28 pb-16 hero-gradient grid-pattern">
          <div className="orb w-[350px] h-[350px] bg-violet-600/15 -top-10 left-20" />
          <div className="relative z-10 mx-auto max-w-7xl px-6">
            <span className="mb-4 inline-block rounded-full glass-card px-4 py-1.5 text-xs font-bold uppercase tracking-wider text-cyan-400">
              Community
            </span>
            <h1 className="mb-3 text-3xl font-extrabold tracking-tight text-white sm:text-4xl">
              함께 질문하고, 공유하고, 기록하는 커뮤니티
            </h1>
            <p className="max-w-2xl break-keep text-lg text-gray-400">
              질문/답변, 학습 공유, 멘토링 후기, 취업/이직, 자유게시판까지 모두 실제 글쓰기와 댓글로 운영됩니다.
            </p>
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-6 py-12">
          <div className="flex flex-col gap-8 lg:flex-row">
            <div className="flex-1">
              <div className="mb-6 flex gap-3">
                <div className="relative flex-1">
                  <Search size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400" />
                  <input
                    type="text"
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                    placeholder="제목, 내용, 작성자를 검색해 보세요"
                    className="w-full rounded-xl border border-gray-200 bg-white py-3 pl-11 pr-4 text-sm text-gray-900 transition-all duration-200 placeholder-gray-400 focus:border-blue-300 focus:outline-none focus:ring-2 focus:ring-blue-100"
                  />
                </div>
                <button
                  type="button"
                  onClick={() => openCreateEditor()}
                  className="whitespace-nowrap rounded-xl bg-gradient-to-r from-blue-600 to-blue-500 px-5 py-3 text-sm font-semibold text-white shadow-lg shadow-blue-600/20 transition-all duration-300 hover:shadow-blue-500/30"
                >
                  <span className="inline-flex items-center gap-2">
                    <PenSquare size={16} />
                    글쓰기
                  </span>
                </button>
              </div>

              <div className="mb-6 flex items-center gap-2 overflow-x-auto pb-2">
                {FILTER_CATEGORIES.map((category) => (
                  <button
                    key={category}
                    type="button"
                    onClick={() => setSelectedCategory(category)}
                    className={`whitespace-nowrap rounded-lg px-4 py-2 text-sm font-medium transition-all duration-200 ${
                      selectedCategory === category
                        ? 'bg-gray-900 text-white'
                        : 'border border-gray-200 bg-white text-gray-500 hover:text-gray-900'
                    }`}
                  >
                    {category}
                  </button>
                ))}
              </div>

              <div className="space-y-3">
                {loading ? (
                  <div className="rounded-xl border border-gray-100 bg-white p-8 text-center text-gray-400">
                    게시글을 불러오는 중입니다.
                  </div>
                ) : filteredPosts.length === 0 ? (
                  <div className="rounded-xl border border-gray-100 bg-white p-16 text-center">
                    <p className="text-lg text-gray-400">아직 등록된 글이 없습니다.</p>
                    <button
                      type="button"
                      onClick={() =>
                        openCreateEditor(selectedCategory === '전체' ? undefined : selectedCategory)
                      }
                      className="mt-4 text-sm font-semibold text-blue-600"
                    >
                      첫 글 작성하기
                    </button>
                  </div>
                ) : (
                  filteredPosts.map((post) => {
                    const score = post.likeCount + post.commentCount + post.viewCount;
                    const hot = score >= 20;
                    return (
                      <article
                        key={post.id}
                        className="group cursor-pointer rounded-xl border border-gray-100 bg-white p-5 transition-all duration-200 hover:border-blue-100 hover:shadow-md hover:shadow-blue-50"
                        onClick={() => void openDetail(post.id)}
                      >
                        <div className="flex items-start justify-between gap-4">
                          <div className="min-w-0 flex-1">
                            <div className="mb-2 flex items-center gap-2">
                              <span className="rounded-md border border-gray-100 bg-gray-50 px-2.5 py-0.5 text-xs font-semibold text-gray-500">
                                {post.category}
                              </span>
                              {hot ? (
                                <span className="rounded-md border border-red-100 bg-red-50 px-2 py-0.5 text-xs font-bold text-red-500">
                                  HOT
                                </span>
                              ) : null}
                            </div>
                            <h3 className="truncate text-base font-semibold text-gray-900 transition-colors group-hover:text-blue-600">
                              {post.title}
                            </h3>
                            <p className="mt-2 line-clamp-2 break-keep text-sm leading-6 text-gray-500">
                              {post.content}
                            </p>
                            <div className="mt-3 flex items-center gap-3 text-xs text-gray-400">
                              <span className="font-medium text-gray-600">{post.authorName}</span>
                              <span className="flex items-center gap-1">
                                <Clock size={12} />
                                {formatRelativeTime(post.createdAt)}
                              </span>
                              <span className="flex items-center gap-1">
                                <Eye size={12} />
                                {formatCount(post.viewCount)}
                              </span>
                            </div>
                          </div>
                          <div className="flex items-center gap-4 pt-1 text-xs text-gray-400">
                            <button
                              type="button"
                              onClick={(event) => {
                                event.stopPropagation();
                                void handleToggleLike(post.id);
                              }}
                              className="flex items-center gap-1"
                            >
                              <Heart size={14} className={post.liked ? 'fill-red-500 text-red-500' : ''} />
                              {post.likeCount}
                            </button>
                            <span className="flex items-center gap-1">
                              <MessageSquare size={14} />
                              {post.commentCount}
                            </span>
                          </div>
                        </div>
                      </article>
                    );
                  })
                )}
              </div>
            </div>

            <aside className="space-y-6 lg:w-80">
              <div className="rounded-2xl border border-gray-100 bg-white p-6">
                <h3 className="mb-4 flex items-center gap-2 font-bold text-gray-900">
                  <TrendingUp size={18} className="text-red-500" />
                  인기 글
                </h3>
                <div className="space-y-3">
                  {hotPosts.map((post, index) => (
                    <button
                      key={post.id}
                      type="button"
                      onClick={() => void openDetail(post.id)}
                      className="flex w-full items-start gap-3 text-left"
                    >
                      <span className="mt-0.5 w-5 font-[Outfit] text-sm font-extrabold text-blue-500">
                        {index + 1}
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-gray-700 transition-colors hover:text-blue-600">
                          {post.title}
                        </p>
                        <span className="text-xs text-gray-400">
                          {post.authorName} · {formatRelativeTime(post.createdAt)}
                        </span>
                      </div>
                    </button>
                  ))}
                </div>
              </div>

              <div className="rounded-2xl border border-gray-100 bg-white p-6">
                <h3 className="mb-4 flex items-center gap-2 font-bold text-gray-900">
                  <Bookmark size={18} className="text-amber-500" />
                  많이 쓰인 키워드
                </h3>
                <div className="flex flex-wrap gap-2">
                  {popularTags.length === 0
                    ? <span className="text-sm text-gray-400">아직 태그가 없습니다.</span>
                    : popularTags.map((tag) => (
                        <button
                          key={tag}
                          type="button"
                          onClick={() => setSearch(tag)}
                          className="rounded-lg border border-gray-100 bg-gray-50 px-3 py-1.5 text-xs font-medium text-gray-600 transition hover:border-blue-100 hover:bg-blue-50 hover:text-blue-600"
                        >
                          #{tag}
                        </button>
                      ))}
                </div>
              </div>
            </aside>
          </div>
        </section>
      </main>

      {editor.open ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 px-4">
          <div className="w-full max-w-2xl rounded-3xl bg-white p-6 shadow-2xl">
            <div className="mb-6 flex items-center justify-between">
              <h2 className="text-2xl font-bold text-gray-900">
                {editor.mode === 'edit' ? '게시글 수정' : '새 게시글 작성'}
              </h2>
              <button type="button" onClick={() => setEditor(initialEditor)} className="text-gray-400">
                <X size={20} />
              </button>
            </div>
            <div className="space-y-4">
              <div>
                <label className="mb-2 block text-sm font-semibold text-gray-700">카테고리</label>
                <select
                  value={editor.category}
                  onChange={(event) =>
                    setEditor((current) => ({ ...current, category: event.target.value as CommunityCategory }))
                  }
                  className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-900"
                >
                  {COMMUNITY_CATEGORIES.map((category) => (
                    <option key={category} value={category}>
                      {category}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="mb-2 block text-sm font-semibold text-gray-700">제목</label>
                <input
                  value={editor.title}
                  onChange={(event) => setEditor((current) => ({ ...current, title: event.target.value }))}
                  className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-900"
                  placeholder="게시글 제목을 입력해 주세요"
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-semibold text-gray-700">내용</label>
                <textarea
                  value={editor.content}
                  onChange={(event) => setEditor((current) => ({ ...current, content: event.target.value }))}
                  rows={10}
                  className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-900"
                  placeholder="질문, 학습 공유, 후기, 취업/이직 이야기, 자유 글을 형식에 맞게 작성해 주세요."
                />
              </div>
            </div>
            <div className="mt-6 flex justify-end gap-3">
              <button
                type="button"
                onClick={() => setEditor(initialEditor)}
                className="rounded-xl border border-gray-200 px-4 py-2 text-sm font-semibold text-gray-600"
              >
                취소
              </button>
              <button
                type="button"
                disabled={submitting}
                onClick={() => void handleSubmitPost()}
                className="rounded-xl bg-gray-900 px-4 py-2 text-sm font-semibold text-white"
              >
                {submitting ? '저장 중...' : editor.mode === 'edit' ? '수정 저장' : '글 등록'}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {selectedPost ? (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/60 px-4 py-6">
          <div className="flex max-h-[90vh] w-full max-w-3xl flex-col overflow-hidden rounded-3xl bg-white shadow-2xl">
            <div className="flex items-start justify-between border-b border-gray-100 px-6 py-5">
              <div className="min-w-0">
                <div className="mb-2 flex items-center gap-2">
                  <span className="rounded-md border border-gray-100 bg-gray-50 px-2.5 py-0.5 text-xs font-semibold text-gray-500">
                    {selectedPost.category}
                  </span>
                  <span className="text-xs text-gray-400">{formatRelativeTime(selectedPost.createdAt)}</span>
                </div>
                <h2 className="break-keep text-2xl font-bold text-gray-900">{selectedPost.title}</h2>
                <p className="mt-2 text-sm text-gray-500">{selectedPost.authorName}</p>
              </div>
              <button type="button" onClick={() => setSelectedPost(null)} className="text-gray-400">
                <X size={20} />
              </button>
            </div>

            <div className="overflow-y-auto px-6 py-5">
              {detailLoading ? (
                <div className="py-10 text-center text-gray-400">게시글을 불러오는 중입니다.</div>
              ) : (
                <>
                  <div className="break-keep whitespace-pre-wrap text-sm leading-7 text-gray-700">
                    {selectedPost.content}
                  </div>

                  <div className="mt-6 flex flex-wrap items-center gap-4 border-y border-gray-100 py-4 text-sm text-gray-500">
                    <button
                      type="button"
                      onClick={() => void handleToggleLike(selectedPost.id)}
                      className="inline-flex items-center gap-2"
                    >
                      <Heart size={16} className={selectedPost.liked ? 'fill-red-500 text-red-500' : ''} />
                      좋아요 {selectedPost.likeCount}
                    </button>
                    <span className="inline-flex items-center gap-2">
                      <Eye size={16} />
                      조회 {selectedPost.viewCount}
                    </span>
                    <span className="inline-flex items-center gap-2">
                      <MessageSquare size={16} />
                      댓글 {selectedPost.commentCount}
                    </span>
                    {user?.id === selectedPost.authorId ? (
                      <div className="ml-auto flex gap-2">
                        <button
                          type="button"
                          onClick={() => openEditEditor(selectedPost)}
                          className="inline-flex items-center gap-1 rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-semibold text-gray-600"
                        >
                          <Pencil size={14} />
                          수정
                        </button>
                        <button
                          type="button"
                          onClick={() => void handleDeletePost(selectedPost.id)}
                          className="inline-flex items-center gap-1 rounded-lg border border-red-200 px-3 py-1.5 text-xs font-semibold text-red-500"
                        >
                          <Trash2 size={14} />
                          삭제
                        </button>
                      </div>
                    ) : null}
                  </div>

                  <div className="mt-6">
                    <h3 className="text-lg font-semibold text-gray-900">댓글</h3>
                    <div className="mt-4 space-y-3">
                      {comments.length === 0 ? (
                        <div className="rounded-2xl bg-gray-50 px-4 py-6 text-center text-sm text-gray-400">
                          아직 댓글이 없습니다.
                        </div>
                      ) : (
                        comments.map((comment) => (
                          <div key={comment.id} className="rounded-2xl border border-gray-100 px-4 py-4">
                            <div className="flex items-center justify-between gap-3">
                              <div>
                                <p className="text-sm font-semibold text-gray-900">{comment.authorName}</p>
                                <p className="text-xs text-gray-400">{formatRelativeTime(comment.createdAt)}</p>
                              </div>
                              {user?.id === comment.authorId ? (
                                <button
                                  type="button"
                                  onClick={() => void handleDeleteComment(comment.id)}
                                  className="text-xs font-semibold text-red-500"
                                >
                                  삭제
                                </button>
                              ) : null}
                            </div>
                            <p className="mt-3 break-keep whitespace-pre-wrap text-sm leading-6 text-gray-700">
                              {comment.content}
                            </p>
                          </div>
                        ))
                      )}
                    </div>

                    <div className="mt-4 rounded-2xl border border-gray-100 bg-gray-50 p-4">
                      <textarea
                        value={commentDraft}
                        onChange={(event) => setCommentDraft(event.target.value)}
                        rows={4}
                        className="w-full resize-none bg-transparent text-sm leading-6 text-gray-900 outline-none"
                        placeholder={
                          isLoggedIn
                            ? selectedPost.category === '질문/답변'
                              ? '답변이나 추가 질문을 남겨 주세요.'
                              : '댓글을 남겨 주세요.'
                            : '로그인 후 댓글을 작성할 수 있습니다.'
                        }
                        disabled={!isLoggedIn}
                      />
                      <div className="mt-3 flex justify-end">
                        <button
                          type="button"
                          disabled={!isLoggedIn || !commentDraft.trim() || submitting}
                          onClick={() => void handleSubmitComment()}
                          className="inline-flex items-center gap-2 rounded-xl bg-gray-900 px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:bg-gray-300"
                        >
                          <Send size={14} />
                          댓글 등록
                        </button>
                      </div>
                    </div>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      ) : null}

      <Footer />
    </>
  );
}
