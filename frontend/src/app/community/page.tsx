'use client';

import { useEffect, useMemo, useRef, useState, type ChangeEvent, type DragEvent } from 'react';
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
  uploadCommunityImage,
} from '@/lib/community';
import {
  Bookmark,
  Clock,
  Eye,
  Heart,
  ImagePlus,
  LoaderCircle,
  MessageSquare,
  PenSquare,
  Pencil,
  Search,
  Send,
  Sparkles,
  Trash2,
  TrendingUp,
  Upload,
  X,
} from 'lucide-react';

const FILTER_CATEGORIES = ['전체', ...COMMUNITY_CATEGORIES] as const;

const CATEGORY_META: Record<
  CommunityCategory,
  {
    title: string;
    description: string;
    titlePlaceholder: string;
    contentPlaceholder: string;
    accentClass: string;
    panelClass: string;
  }
> = {
  '질문/답변': {
    title: '질문/답변',
    description: '문제 상황, 시도한 내용, 원하는 답변을 함께 남겨보세요.',
    titlePlaceholder: '예: React 상태가 갱신되지 않는 이유가 뭘까요?',
    contentPlaceholder: '현재 상황, 시도한 코드, 에러 메시지, 원하는 답변을 자세히 적어주세요.',
    accentClass: 'from-blue-500/20 via-cyan-400/10 to-transparent',
    panelClass: 'border-blue-400/30 bg-blue-500/10',
  },
  '학습 공유': {
    title: '학습 공유',
    description: '배운 점, 자료, 실습 팁을 다른 사람과 나눠보세요.',
    titlePlaceholder: '예: 스프링 트랜잭션 정리와 실수 포인트',
    contentPlaceholder: '공유하고 싶은 학습 내용, 추천 자료, 핵심 요약을 적어주세요.',
    accentClass: 'from-emerald-500/20 via-teal-400/10 to-transparent',
    panelClass: 'border-emerald-400/30 bg-emerald-500/10',
  },
  '멘토링 후기': {
    title: '멘토링 후기',
    description: '도움받은 점과 변화한 부분을 구체적으로 남겨보세요.',
    titlePlaceholder: '예: 4주 멘토링 후 포트폴리오가 달라진 점',
    contentPlaceholder: '무엇이 좋았는지, 어떤 변화가 있었는지 실제 경험 중심으로 적어주세요.',
    accentClass: 'from-violet-500/20 via-fuchsia-400/10 to-transparent',
    panelClass: 'border-violet-400/30 bg-violet-500/10',
  },
  '취업/이직': {
    title: '취업/이직',
    description: '지원 과정, 면접 후기, 이직 고민을 공유해보세요.',
    titlePlaceholder: '예: 프론트엔드 면접에서 자주 받은 질문 정리',
    contentPlaceholder: '지원 과정, 결과, 느낀 점, 팁을 함께 적어주세요.',
    accentClass: 'from-amber-500/20 via-orange-400/10 to-transparent',
    panelClass: 'border-amber-400/30 bg-amber-500/10',
  },
  자유게시판: {
    title: '자유게시판',
    description: '개발 일상부터 가벼운 잡담까지 편하게 이야기해보세요.',
    titlePlaceholder: '예: 요즘 가장 자주 쓰는 개발 생산성 툴은?',
    contentPlaceholder: '자유롭게 이야기하고 싶은 내용을 적어주세요.',
    accentClass: 'from-slate-400/20 via-slate-300/10 to-transparent',
    panelClass: 'border-slate-400/30 bg-slate-400/10',
  },
};

type EditorState = {
  open: boolean;
  mode: 'create' | 'edit';
  targetId: number | null;
  category: CommunityCategory;
  title: string;
  content: string;
  imageUrl: string;
};

const initialEditor: EditorState = {
  open: false,
  mode: 'create',
  targetId: null,
  category: '질문/답변',
  title: '',
  content: '',
  imageUrl: '',
};

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

export default function CommunityPage() {
  const { user, isLoggedIn } = useAuth();
  const fileInputRef = useRef<HTMLInputElement | null>(null);
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
  const [uploadingImage, setUploadingImage] = useState(false);
  const [dragActive, setDragActive] = useState(false);

  const activeCategory = selectedCategory === '전체' ? null : selectedCategory;

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
    const keyword = search.trim().toLowerCase();
    return posts.filter((post) => {
      const matchesCategory = !activeCategory || post.category === activeCategory;
      const matchesSearch =
        keyword.length === 0 ||
        post.title.toLowerCase().includes(keyword) ||
        post.content.toLowerCase().includes(keyword) ||
        post.authorName.toLowerCase().includes(keyword);
      return matchesCategory && matchesSearch;
    });
  }, [activeCategory, posts, search]);

  const hotPosts = useMemo(
    () =>
      [...posts]
        .sort((a, b) => b.likeCount + b.commentCount + b.viewCount - (a.likeCount + a.commentCount + a.viewCount))
        .slice(0, 4),
    [posts]
  );

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

  const categoryCounts = useMemo(
    () =>
      COMMUNITY_CATEGORIES.reduce(
        (acc, category) => ({ ...acc, [category]: posts.filter((post) => post.category === category).length }),
        {} as Record<CommunityCategory, number>
      ),
    [posts]
  );

  const closeEditor = () => {
    setEditor(initialEditor);
    setDragActive(false);
    setUploadingImage(false);
  };

  const openDetail = async (postId: number) => {
    setDetailLoading(true);
    try {
      const [postResponse, commentResponse] = await Promise.all([getCommunityPost(postId), getCommunityComments(postId)]);
      setSelectedPost(postResponse.data);
      setComments(commentResponse.data);
      setPosts((current) => current.map((item) => (item.id === postResponse.data.id ? postResponse.data : item)));
    } catch (error) {
      console.error(error);
    } finally {
      setDetailLoading(false);
    }
  };

  const handleUploadImage = async (file: File) => {
    if (!file.type.startsWith('image/')) {
      alert('이미지 파일만 업로드할 수 있습니다.');
      return;
    }

    setUploadingImage(true);
    try {
      const response = await uploadCommunityImage(file);
      setEditor((current) => ({ ...current, imageUrl: response.data.imageUrl }));
    } catch (error) {
      console.error(error);
      alert('이미지 업로드에 실패했습니다.');
    } finally {
      setUploadingImage(false);
      setDragActive(false);
    }
  };

  const handleFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      await handleUploadImage(file);
    }
    event.target.value = '';
  };

  const handleDrop = async (event: DragEvent<HTMLButtonElement>) => {
    event.preventDefault();
    setDragActive(false);
    const file = event.dataTransfer.files?.[0];
    if (file) {
      await handleUploadImage(file);
    }
  };

  const handleSubmitPost = async () => {
    if (!isLoggedIn) {
      alert('로그인 후 글을 작성할 수 있습니다.');
      return;
    }

    if (!editor.title.trim() || !editor.content.trim()) {
      alert('카테고리, 제목, 내용을 모두 입력해주세요.');
      return;
    }

    setSubmitting(true);
    try {
      if (editor.mode === 'edit' && editor.targetId) {
        const response = await updateCommunityPost(editor.targetId, {
          category: editor.category,
          title: editor.title.trim(),
          content: editor.content.trim(),
          imageUrl: editor.imageUrl || undefined,
        });
        const updated = response.data;
        setPosts((current) => current.map((post) => (post.id === updated.id ? updated : post)));
        setSelectedPost((current) => (current?.id === updated.id ? updated : current));
        setSelectedCategory(updated.category);
      } else {
        const response = await createCommunityPost({
          category: editor.category,
          title: editor.title.trim(),
          content: editor.content.trim(),
          imageUrl: editor.imageUrl || undefined,
        });
        setPosts((current) => [response.data, ...current]);
        setSelectedCategory(response.data.category);
      }

      closeEditor();
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
    if (!confirm('이 게시글을 삭제할까요?')) {
      return;
    }

    try {
      await deleteCommunityPost(postId);
      setPosts((current) => current.filter((post) => post.id !== postId));
      setSelectedPost((current) => (current?.id === postId ? null : current));
      setComments([]);
    } catch (error) {
      console.error(error);
      alert('게시글 삭제에 실패했습니다.');
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
      await createCommunityComment(selectedPost.id, commentDraft.trim());
      setCommentDraft('');
      await openDetail(selectedPost.id);
    } catch (error) {
      console.error(error);
      alert('댓글 작성에 실패했습니다.');
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
      alert('댓글 삭제에 실패했습니다.');
    }
  };

  const openCreateEditor = (category?: CommunityCategory) => {
    setEditor({
      ...initialEditor,
      open: true,
      category: category ?? (activeCategory || '질문/답변'),
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
      imageUrl: post.imageUrl ?? '',
    });
  };

  return (
    <>
      <Header />
      <main className="min-h-screen bg-[#090d18] text-white">
        <section className="relative overflow-hidden border-b border-white/5 bg-[radial-gradient(circle_at_top_left,_rgba(59,130,246,0.18),_transparent_28%),radial-gradient(circle_at_top_right,_rgba(34,197,94,0.12),_transparent_24%),linear-gradient(180deg,_#0d1322_0%,_#090d18_72%)] pb-14 pt-28">
          <div className="mx-auto max-w-7xl px-6">
            <div className="inline-flex items-center gap-2 rounded-full border border-blue-400/20 bg-blue-400/10 px-4 py-1.5 text-xs font-semibold text-blue-200">
              <Sparkles size={14} />
              Community Board
            </div>
            <h1 className="mt-5 text-3xl font-extrabold tracking-tight text-white sm:text-4xl">
              카테고리별로 바로 보이고, 이미지도 직접 올리는 커뮤니티
            </h1>
            <p className="mt-4 max-w-3xl break-keep text-sm leading-7 text-slate-300 sm:text-base">
              질문/답변, 학습 공유, 멘토링 후기, 취업/이직, 자유게시판이 각각 따로 보이도록 정리했고,
              이제 폴더 선택이나 드래그앤드롭으로 이미지를 첨부할 수 있습니다.
            </p>
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-6 py-10">
          <div className="mb-6 grid gap-3 md:grid-cols-2 xl:grid-cols-5">
            {COMMUNITY_CATEGORIES.map((category) => {
              const active = activeCategory === category;
              return (
                <button
                  key={category}
                  type="button"
                  onClick={() => {
                    setSelectedCategory(category);
                    setSearch('');
                  }}
                  className={`overflow-hidden rounded-3xl border p-4 text-left transition-all duration-200 ${
                    active
                      ? 'border-blue-400/40 bg-slate-900/90 shadow-[0_0_0_1px_rgba(96,165,250,0.15)]'
                      : 'border-white/10 bg-white/5 hover:border-blue-300/20 hover:bg-white/[0.07]'
                  }`}
                >
                  <div className={`rounded-2xl bg-gradient-to-br p-4 ${CATEGORY_META[category].accentClass}`}>
                    <p className="text-base font-bold text-white">{category}</p>
                    <p className="mt-2 break-keep text-xs leading-5 text-slate-300">{CATEGORY_META[category].description}</p>
                  </div>
                  <div className="mt-3 flex items-center justify-between text-xs">
                    <span className="text-slate-400">게시글 {categoryCounts[category] ?? 0}</span>
                    <span className="font-semibold text-blue-300">{active ? '선택됨' : '보드 보기'}</span>
                  </div>
                </button>
              );
            })}
          </div>

          <div className="flex flex-col gap-8 lg:flex-row">
            <div className="flex-1">
              <div className="mb-6 flex flex-col gap-3 sm:flex-row">
                <div className="relative flex-1">
                  <Search size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500" />
                  <input
                    type="text"
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                    placeholder="제목, 내용, 작성자를 검색해 보세요"
                    className="w-full rounded-2xl border border-white/10 bg-white/5 py-3 pl-11 pr-4 text-sm text-white placeholder:text-slate-500 focus:border-blue-400/40 focus:outline-none"
                  />
                </div>
                <button
                  type="button"
                  onClick={() => openCreateEditor()}
                  className="rounded-2xl bg-blue-500 px-5 py-3 text-sm font-semibold text-white transition hover:bg-blue-400"
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
                    className={`whitespace-nowrap rounded-full px-4 py-2 text-sm font-semibold transition ${
                      selectedCategory === category
                        ? 'bg-blue-500 text-white'
                        : 'border border-white/10 bg-white/5 text-slate-300 hover:border-blue-300/30 hover:text-white'
                    }`}
                  >
                    {category}
                  </button>
                ))}
              </div>

              {activeCategory ? (
                <div className={`mb-6 rounded-3xl border p-5 ${CATEGORY_META[activeCategory].panelClass}`}>
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                      <p className="text-xs font-bold uppercase tracking-[0.22em] text-slate-300">Selected Board</p>
                      <h2 className="mt-2 text-2xl font-bold text-white">{CATEGORY_META[activeCategory].title}</h2>
                      <p className="mt-2 max-w-2xl break-keep text-sm leading-6 text-slate-200">
                        {CATEGORY_META[activeCategory].description}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => openCreateEditor(activeCategory)}
                      className="rounded-2xl border border-white/10 bg-slate-950/60 px-4 py-2 text-sm font-semibold text-white"
                    >
                      이 카테고리 글쓰기
                    </button>
                  </div>
                </div>
              ) : null}

              <div className="space-y-4">
                {loading ? (
                  <div className="rounded-3xl border border-white/10 bg-white/5 p-10 text-center text-slate-400">
                    게시글을 불러오는 중입니다.
                  </div>
                ) : filteredPosts.length === 0 ? (
                  <div className="rounded-3xl border border-dashed border-white/10 bg-white/5 p-16 text-center">
                    <p className="text-lg text-slate-300">아직 작성된 글이 없습니다.</p>
                    <button
                      type="button"
                      onClick={() => openCreateEditor(activeCategory || undefined)}
                      className="mt-4 text-sm font-semibold text-blue-300"
                    >
                      첫 게시글 작성하기
                    </button>
                  </div>
                ) : (
                  filteredPosts.map((post) => {
                    const score = post.likeCount + post.commentCount + post.viewCount;
                    const hot = score >= 20;

                    return (
                      <article
                        key={post.id}
                        className="group cursor-pointer rounded-3xl border border-white/10 bg-[linear-gradient(180deg,rgba(15,23,42,0.88),rgba(9,13,24,0.96))] p-5 transition hover:border-blue-300/20 hover:bg-[linear-gradient(180deg,rgba(15,23,42,0.98),rgba(9,13,24,1))]"
                        onClick={() => void openDetail(post.id)}
                      >
                        <div className="flex items-start justify-between gap-4">
                          <div className="min-w-0 flex-1">
                            <div className="mb-3 flex flex-wrap items-center gap-2">
                              <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs font-semibold text-slate-200">
                                {post.category}
                              </span>
                              {hot ? (
                                <span className="rounded-full border border-red-400/20 bg-red-500/10 px-3 py-1 text-xs font-bold text-red-300">
                                  HOT
                                </span>
                              ) : null}
                            </div>

                            <h3 className="break-keep text-lg font-semibold text-white transition group-hover:text-blue-300">
                              {post.title}
                            </h3>

                            {post.imageUrl ? (
                              <div className="mt-4 overflow-hidden rounded-3xl border border-white/10 bg-slate-950/80">
                                {/* eslint-disable-next-line @next/next/no-img-element */}
                                <img src={post.imageUrl} alt={post.title} className="h-52 w-full object-contain" />
                              </div>
                            ) : null}

                            <p className="mt-4 line-clamp-3 break-keep text-sm leading-7 text-slate-300">{post.content}</p>

                            <div className="mt-4 flex flex-wrap items-center gap-3 text-xs text-slate-400">
                              <span className="font-medium text-slate-200">{post.authorName}</span>
                              <span className="inline-flex items-center gap-1">
                                <Clock size={12} />
                                {formatRelativeTime(post.createdAt)}
                              </span>
                              <span className="inline-flex items-center gap-1">
                                <Eye size={12} />
                                {post.viewCount}
                              </span>
                            </div>
                          </div>

                          <div className="flex items-center gap-4 pt-1 text-xs text-slate-400">
                            <button
                              type="button"
                              onClick={(event) => {
                                event.stopPropagation();
                                void handleToggleLike(post.id);
                              }}
                              className="inline-flex items-center gap-1"
                            >
                              <Heart size={14} className={post.liked ? 'fill-red-500 text-red-400' : ''} />
                              {post.likeCount}
                            </button>
                            <span className="inline-flex items-center gap-1">
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
              <div className="rounded-3xl border border-white/10 bg-white/5 p-6">
                <h3 className="mb-4 flex items-center gap-2 text-base font-bold text-white">
                  <TrendingUp size={18} className="text-blue-300" />
                  인기 게시글
                </h3>
                <div className="space-y-3">
                  {hotPosts.map((post, index) => (
                    <button
                      key={post.id}
                      type="button"
                      onClick={() => void openDetail(post.id)}
                      className="flex w-full items-start gap-3 text-left"
                    >
                      <span className="mt-0.5 w-5 text-sm font-extrabold text-blue-300">{index + 1}</span>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-slate-100">{post.title}</p>
                        <span className="text-xs text-slate-400">
                          {post.authorName} · {formatRelativeTime(post.createdAt)}
                        </span>
                      </div>
                    </button>
                  ))}
                </div>
              </div>

              <div className="rounded-3xl border border-white/10 bg-white/5 p-6">
                <h3 className="mb-4 flex items-center gap-2 text-base font-bold text-white">
                  <Bookmark size={18} className="text-amber-300" />
                  인기 키워드
                </h3>
                <div className="flex flex-wrap gap-2">
                  {popularTags.length === 0 ? (
                    <span className="text-sm text-slate-400">아직 집계된 키워드가 없습니다.</span>
                  ) : (
                    popularTags.map((tag) => (
                      <button
                        key={tag}
                        type="button"
                        onClick={() => setSearch(tag)}
                        className="rounded-full border border-white/10 bg-slate-950/70 px-3 py-1.5 text-xs font-medium text-slate-300 transition hover:border-blue-300/30 hover:text-blue-200"
                      >
                        #{tag}
                      </button>
                    ))
                  )}
                </div>
              </div>
            </aside>
          </div>
        </section>
      </main>

      {editor.open ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 px-4 py-6">
          <div className="max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-[32px] border border-white/10 bg-[#0d1322] p-6 shadow-2xl">
            <div className="mb-6 flex items-start justify-between gap-4">
              <div>
                <h2 className="text-2xl font-bold text-white">{editor.mode === 'edit' ? '게시글 수정' : '새 게시글 작성'}</h2>
                <p className="mt-2 text-sm leading-6 text-slate-300">{CATEGORY_META[editor.category].description}</p>
              </div>
              <button type="button" onClick={closeEditor} className="rounded-full border border-white/10 p-2 text-slate-400">
                <X size={18} />
              </button>
            </div>

            <div className="space-y-5">
              <div>
                <label className="mb-2 block text-sm font-semibold text-slate-200">카테고리</label>
                <select
                  value={editor.category}
                  onChange={(event) => setEditor((current) => ({ ...current, category: event.target.value as CommunityCategory }))}
                  className="w-full rounded-2xl border border-white/10 bg-slate-950/70 px-4 py-3 text-sm text-white outline-none"
                >
                  {COMMUNITY_CATEGORIES.map((category) => (
                    <option key={category} value={category}>
                      {category}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="mb-2 block text-sm font-semibold text-slate-200">제목</label>
                <input
                  value={editor.title}
                  onChange={(event) => setEditor((current) => ({ ...current, title: event.target.value }))}
                  className="w-full rounded-2xl border border-white/10 bg-slate-950/70 px-4 py-3 text-sm text-white placeholder:text-slate-500 outline-none"
                  placeholder={CATEGORY_META[editor.category].titlePlaceholder}
                />
              </div>

              <div>
                <label className="mb-2 block text-sm font-semibold text-slate-200">내용</label>
                <textarea
                  value={editor.content}
                  onChange={(event) => setEditor((current) => ({ ...current, content: event.target.value }))}
                  rows={10}
                  className="w-full rounded-2xl border border-white/10 bg-slate-950/70 px-4 py-3 text-sm leading-7 text-white placeholder:text-slate-500 outline-none"
                  placeholder={CATEGORY_META[editor.category].contentPlaceholder}
                />
              </div>

              <div>
                <div className="mb-2 flex items-center justify-between gap-3">
                  <label className="block text-sm font-semibold text-slate-200">대표 이미지</label>
                  {editor.imageUrl ? (
                    <button
                      type="button"
                      onClick={() => setEditor((current) => ({ ...current, imageUrl: '' }))}
                      className="text-xs font-semibold text-red-300"
                    >
                      이미지 제거
                    </button>
                  ) : null}
                </div>

                <input ref={fileInputRef} type="file" accept="image/*" className="hidden" onChange={(event) => void handleFileChange(event)} />

                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  onDragOver={(event) => {
                    event.preventDefault();
                    setDragActive(true);
                  }}
                  onDragLeave={() => setDragActive(false)}
                  onDrop={(event) => void handleDrop(event)}
                  className={`w-full rounded-[28px] border border-dashed px-6 py-8 text-left transition ${
                    dragActive
                      ? 'border-blue-300 bg-blue-500/10'
                      : 'border-white/15 bg-slate-950/60 hover:border-blue-300/30 hover:bg-slate-900'
                  }`}
                >
                  <div className="flex flex-col items-center justify-center gap-3 text-center">
                    {uploadingImage ? (
                      <LoaderCircle size={28} className="animate-spin text-blue-300" />
                    ) : (
                      <div className="rounded-full border border-white/10 bg-white/5 p-3 text-blue-200">
                        <Upload size={24} />
                      </div>
                    )}
                    <div>
                      <p className="text-sm font-semibold text-white">폴더에서 이미지 선택 또는 여기로 드래그앤드롭</p>
                      <p className="mt-1 text-xs leading-5 text-slate-400">
                        JPG, PNG, WEBP 파일을 업로드하면 게시글 카드와 상세에서 바로 보여집니다.
                      </p>
                    </div>
                    <span className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs font-medium text-slate-300">
                      <ImagePlus size={14} />
                      이미지 업로드
                    </span>
                  </div>
                </button>

                {editor.imageUrl ? (
                  <div className="mt-4 overflow-hidden rounded-[28px] border border-white/10 bg-slate-950/80">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={editor.imageUrl} alt="미리보기" className="h-64 w-full object-contain" />
                  </div>
                ) : null}
              </div>
            </div>

            <div className="mt-6 flex justify-end gap-3">
              <button
                type="button"
                onClick={closeEditor}
                className="rounded-2xl border border-white/10 px-4 py-2 text-sm font-semibold text-slate-300"
              >
                취소
              </button>
              <button
                type="button"
                disabled={submitting || uploadingImage}
                onClick={() => void handleSubmitPost()}
                className="rounded-2xl bg-blue-500 px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:bg-slate-600"
              >
                {submitting ? '저장 중...' : editor.mode === 'edit' ? '수정 저장' : '게시글 등록'}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {selectedPost ? (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/80 px-4 py-6">
          <div className="flex max-h-[92vh] w-full max-w-3xl flex-col overflow-hidden rounded-[32px] border border-white/10 bg-[#0d1322] shadow-2xl">
            <div className="flex items-start justify-between border-b border-white/10 px-6 py-5">
              <div className="min-w-0">
                <div className="mb-2 flex flex-wrap items-center gap-2">
                  <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs font-semibold text-slate-200">
                    {selectedPost.category}
                  </span>
                  <span className="text-xs text-slate-400">{formatRelativeTime(selectedPost.createdAt)}</span>
                </div>
                <h2 className="break-keep text-2xl font-bold text-white">{selectedPost.title}</h2>
                <p className="mt-2 text-sm text-slate-400">{selectedPost.authorName}</p>
              </div>
              <button type="button" onClick={() => setSelectedPost(null)} className="rounded-full border border-white/10 p-2 text-slate-400">
                <X size={18} />
              </button>
            </div>

            <div className="overflow-y-auto px-6 py-5">
              {detailLoading ? (
                <div className="py-10 text-center text-slate-400">게시글을 불러오는 중입니다.</div>
              ) : (
                <>
                  {selectedPost.imageUrl ? (
                    <div className="mb-5 overflow-hidden rounded-[28px] border border-white/10 bg-slate-950/80">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img src={selectedPost.imageUrl} alt={selectedPost.title} className="h-80 w-full object-contain" />
                    </div>
                  ) : null}

                  <div className="break-keep whitespace-pre-wrap text-sm leading-8 text-slate-200">{selectedPost.content}</div>

                  <div className="mt-6 flex flex-wrap items-center gap-4 border-y border-white/10 py-4 text-sm text-slate-400">
                    <button type="button" onClick={() => void handleToggleLike(selectedPost.id)} className="inline-flex items-center gap-2">
                      <Heart size={16} className={selectedPost.liked ? 'fill-red-500 text-red-400' : ''} />
                      좋아요 {selectedPost.likeCount}
                    </button>
                    <span className="inline-flex items-center gap-2">
                      <Eye size={16} />
                      조회수 {selectedPost.viewCount}
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
                          className="inline-flex items-center gap-1 rounded-xl border border-white/10 px-3 py-1.5 text-xs font-semibold text-slate-200"
                        >
                          <Pencil size={14} />
                          수정
                        </button>
                        <button
                          type="button"
                          onClick={() => void handleDeletePost(selectedPost.id)}
                          className="inline-flex items-center gap-1 rounded-xl border border-red-400/20 px-3 py-1.5 text-xs font-semibold text-red-300"
                        >
                          <Trash2 size={14} />
                          삭제
                        </button>
                      </div>
                    ) : null}
                  </div>

                  <div className="mt-6">
                    <h3 className="text-lg font-semibold text-white">댓글</h3>
                    <div className="mt-4 space-y-3">
                      {comments.length === 0 ? (
                        <div className="rounded-3xl border border-white/10 bg-white/5 px-4 py-6 text-center text-sm text-slate-400">
                          아직 댓글이 없습니다.
                        </div>
                      ) : (
                        comments.map((comment) => (
                          <div key={comment.id} className="rounded-3xl border border-white/10 bg-white/5 px-4 py-4">
                            <div className="flex items-center justify-between gap-3">
                              <div>
                                <p className="text-sm font-semibold text-white">{comment.authorName}</p>
                                <p className="text-xs text-slate-400">{formatRelativeTime(comment.createdAt)}</p>
                              </div>
                              {user?.id === comment.authorId ? (
                                <button type="button" onClick={() => void handleDeleteComment(comment.id)} className="text-xs font-semibold text-red-300">
                                  삭제
                                </button>
                              ) : null}
                            </div>
                            <p className="mt-3 break-keep whitespace-pre-wrap text-sm leading-7 text-slate-200">{comment.content}</p>
                          </div>
                        ))
                      )}
                    </div>

                    <div className="mt-4 rounded-3xl border border-white/10 bg-white/5 p-4">
                      <textarea
                        value={commentDraft}
                        onChange={(event) => setCommentDraft(event.target.value)}
                        rows={4}
                        className="w-full resize-none bg-transparent text-sm leading-7 text-white outline-none placeholder:text-slate-500"
                        placeholder={isLoggedIn ? '댓글을 작성해보세요.' : '로그인 후 댓글을 작성할 수 있습니다.'}
                        disabled={!isLoggedIn}
                      />
                      <div className="mt-3 flex justify-end">
                        <button
                          type="button"
                          disabled={!isLoggedIn || !commentDraft.trim() || submitting}
                          onClick={() => void handleSubmitComment()}
                          className="inline-flex items-center gap-2 rounded-2xl bg-blue-500 px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:bg-slate-600"
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
