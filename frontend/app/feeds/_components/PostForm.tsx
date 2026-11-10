'use client';
import styles from './PostForm.module.css';
import { useEffect, useId, useRef, useState } from 'react';
import { useNavigation as useNavigate } from '@/lib/navigation';
import { Image as ImageIcon, Plus, X } from 'lucide-react';
import { ProfileTagEditor } from '@/components/profile/ProfileTagEditor';
import { interestOptions, stackOptions } from '@/mocks/home';
import {
    FEED_CATEGORIES, FEED_CONTENT_MAX, FEED_IMAGE_MAX_COUNT, FEED_IMAGE_MESSAGE, FEED_IMAGE_TYPES, FEED_TITLE_MAX,
    saveFeedPost, validateFeedDraft, validFeedImage,
} from '@/lib/social/feedApi';
import { Input, Textarea } from '@/components/common/Input';
import { Button } from '@/components/common/Button';
import type { FeedCategoryCode, FeedPost, FeedPostDraft } from '@/types/feedApi';

type Fields = Omit<FeedPostDraft, 'files'>;
type NewImage = { file: File; url: string };
const scope = (name: string) => [styles.scope, name].join(' ');

/**
 * 게시글 작성·수정 폼. 저장은 multipart/form-data로 보내고(글 내용은 JSON 파트, 이미지는 파일 파트),
 * 저장에 실패하면 입력과 첨부한 이미지를 그대로 둔다. 저장 중에는 다시 보낼 수 없다.
 */
export function PostForm({ post }: { post?: FeedPost }) {
    const navigate = useNavigate();
    const inputId = useId();
    const imageInput = useRef<HTMLInputElement>(null);
    const [fields, setFields] = useState<Fields>(() => ({
        category: post?.category ?? 'INFO', title: post?.title ?? '', content: post?.content ?? '',
        techStacks: [...(post?.techStacks ?? [])], interests: [...(post?.interests ?? [])], keepImageUrls: [...(post?.imageUrls ?? [])],
    }));
    const [images, setImages] = useState<NewImage[]>([]);
    const [busy, setBusy] = useState(false);
    const [errors, setErrors] = useState<string[]>([]);
    // 미리보기 주소는 화면을 떠날 때 모두 해제한다. 렌더와 상관없는 기록이라 바뀌지 않는 Set 하나에 모은다.
    const [previewUrls] = useState(() => new Set<string>());
    useEffect(() => () => previewUrls.forEach(url => URL.revokeObjectURL(url)), [previewUrls]);

    const edit = <K extends keyof Fields>(name: K, value: Fields[K]) => {
        setFields(current => ({ ...current, [name]: value }));
        setErrors([]);
    };
    const imageCount = fields.keepImageUrls.length + images.length;

    function addImages(files: File[]) {
        if (!files.length) return;
        if (imageCount + files.length > FEED_IMAGE_MAX_COUNT || !files.every(validFeedImage)) {
            setErrors([FEED_IMAGE_MESSAGE]);
            return;
        }
        const added = files.map(file => ({ file, url: URL.createObjectURL(file) }));
        added.forEach(item => previewUrls.add(item.url));
        setImages(current => [...current, ...added]);
        setErrors([]);
    }

    function removeImage(target: NewImage) {
        URL.revokeObjectURL(target.url);
        previewUrls.delete(target.url);
        setImages(current => current.filter(item => item !== target));
    }

    async function submit() {
        if (busy) return;
        const draft: FeedPostDraft = { ...fields, files: images.map(item => item.file) };
        const found = validateFeedDraft(draft);
        setErrors(found);
        if (found.length) return;
        setBusy(true);
        try {
            const saved = await saveFeedPost(draft, post?.id);
            navigate(`/feeds/${saved.id}`);
        } catch (error) {
            setErrors([error instanceof Error ? error.message : '게시글을 저장하지 못했어요. 입력 내용은 그대로 있어요.']);
            setBusy(false);
        }
    }

    return <form className={scope('profile-form sns-editor')} noValidate onSubmit={event => { event.preventDefault(); void submit(); }}>
        <label>카테고리<select className={scope('form-input')} value={fields.category} disabled={busy}
            onChange={event => edit('category', event.target.value as FeedCategoryCode)}>
            {FEED_CATEGORIES.map(item => <option key={item.code} value={item.code}>{item.label}</option>)}
        </select></label>
        <label>제목<Input value={fields.title} maxLength={FEED_TITLE_MAX} disabled={busy} onChange={event => edit('title', event.target.value)}
            placeholder="어떤 이야기를 나누고 싶으세요?" /></label>
        <label>본문<Textarea value={fields.content} rows={12} maxLength={FEED_CONTENT_MAX} disabled={busy}
            onChange={event => edit('content', event.target.value)} placeholder="경험과 고민을 편하게 들려주세요. HTML은 실행하지 않습니다." /></label>
        <fieldset disabled={busy} className={styles.tagFields}>
            <ProfileTagEditor label="기술 스택 태그" options={stackOptions} value={fields.techStacks} onChange={value => edit('techStacks', value)} />
            <ProfileTagEditor label="관심 분야" options={interestOptions} value={fields.interests} onChange={value => edit('interests', value)} />
        </fieldset>
        <div className={scope('sns-upload')}>
            <div className={styles.uploadHeading}>
                <span>이미지 <span className={styles.imageCount}>{imageCount}/{FEED_IMAGE_MAX_COUNT}</span></span>
                <button type="button" className={styles.addImage} aria-label="이미지 추가" title="이미지 추가"
                    disabled={busy || imageCount >= FEED_IMAGE_MAX_COUNT} onClick={() => imageInput.current?.click()}><Plus size={20} /></button>
            </div>
            <input ref={imageInput} hidden id={inputId} type="file" accept={FEED_IMAGE_TYPES.join(',')} multiple disabled={busy}
                onChange={event => { addImages(Array.from(event.currentTarget.files ?? [])); event.currentTarget.value = ''; }} />
            <p className={scope('auth-hint')}>최대 3장 · 각 512KB 이하 · PNG/JPEG/WebP/GIF · 이미지는 저장할 때 서버에 올라갑니다.</p>
            <div className={scope('sns-image-previews')}>
                {!imageCount && <button type="button" className={styles.emptyImage} aria-label="첫 이미지 추가" disabled={busy}
                    onClick={() => imageInput.current?.click()}><ImageIcon size={30} /><span>이미지로 이야기를 더해 보세요</span></button>}
                {fields.keepImageUrls.map((url, index) => <div className={styles.preview} key={url}>
                    <img src={url} alt={`첨부한 이미지 ${index + 1}`} />
                    <button type="button" className={styles.removeImage} aria-label={`첨부한 이미지 ${index + 1} 제거`} disabled={busy}
                        onClick={() => edit('keepImageUrls', fields.keepImageUrls.filter(item => item !== url))}><X size={15} /></button>
                </div>)}
                {images.map(item => <div className={styles.preview} key={item.url}>
                    <img src={item.url} alt={item.file.name} />
                    <button type="button" className={styles.removeImage} aria-label={`${item.file.name} 이미지 제거`} disabled={busy}
                        onClick={() => removeImage(item)}><X size={15} /></button>
                </div>)}
            </div>
        </div>
        {errors.length > 0 && <ul className={scope('form-error')} role="alert">{errors.map(error => <li key={error}>{error}</li>)}</ul>}
        <div className={scope('modal-actions')}>
            <Button type="button" variant="secondary" disabled={busy} onClick={() => navigate(post ? `/feeds/${post.id}` : '/feeds')}>취소</Button>
            <Button type="submit" disabled={busy}>{busy ? '저장하는 중…' : post ? '수정 저장' : '게시글 등록'}</Button>
        </div>
    </form>;
}
