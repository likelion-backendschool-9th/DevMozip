'use client';
import styles from './PostForm.module.css';
import { useId, useRef, useState } from 'react';
import { useNavigation as useNavigate } from '@/lib/navigation';
import { Plus, Image, X } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import { ProfileTagEditor } from '@/components/profile/ProfileTagEditor';
import { categories, stackOptions, interestOptions } from '@/mocks/home';
import { emptyPost, imagePolicy } from '@/mocks/social';
import { validatePost } from '@/lib/social/socialService';
import { createMockId } from '@/lib/utils/createMockId';
import { Input, Textarea } from '@/components/common/Input';
import { Button } from '@/components/common/Button';
import type { PostImage, PostInput } from '@/types/social';
import type { ProfilePost } from '@/types/profile';
function readImage(file: File): Promise<PostImage> { return new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => typeof reader.result === 'string' ? resolve({ id: createMockId(), name: file.name, url: reader.result, type: file.type, size: file.size }) : reject(new Error('이미지를 읽지 못했습니다.')); reader.onerror = () => reject(new Error('이미지를 읽지 못했습니다.')); reader.onabort = () => reject(new Error('이미지 읽기가 취소되었습니다.')); reader.readAsDataURL(file); }); }
export function PostForm({ post }: {
    post?: ProfilePost;
}) { const { user } = useAuth(); const { act } = useProfiles(); const navigate = useNavigate(); const inputId = useId(); const imageInput = useRef<HTMLInputElement>(null); const [form, setForm] = useState<PostInput>(() => post ? { title: post.title, body: post.body, category: post.category, tags: [...post.tags], interests: [...(post.interests || [])], images: [...(post.images || [])], github: post.github || '', external: post.external || '' } : structuredClone(emptyPost)); const [busy, setBusy] = useState(false); const [errors, setErrors] = useState<string[]>([]); const edit = <K extends keyof PostInput>(key: K, value: PostInput[K]) => { setForm(prev => ({ ...prev, [key]: value })); setErrors([]); }; return <form className={[styles.scope, "profile-form sns-editor"].join(" ")} noValidate onSubmit={e => { e.preventDefault(); if (busy)
    return; const errors = validatePost(form); setErrors(errors); if (errors.length)
    return; const id = post?.id || createMockId(); if (act({ type: post ? 'social-update' : 'social-create', id, input: form }))
    navigate(`/feeds/${id}`); }}><label>카테고리<select className={[styles.scope, "form-input"].join(" ")} value={form.category} onChange={e => edit('category', e.target.value as PostInput['category'])} disabled={busy}>{categories.slice(1).map(c => <option key={c}>{c}</option>)}</select></label><label>제목<Input value={form.title} maxLength={150} onChange={e => edit('title', e.target.value)} placeholder="어떤 이야기를 나누고 싶으세요?" disabled={busy}/></label><label>본문<Textarea value={form.body} rows={12} maxLength={10000} onChange={e => edit('body', e.target.value)} placeholder="경험과 고민을 편하게 들려주세요. HTML은 실행하지 않습니다." disabled={busy}/></label><fieldset disabled={busy} className={styles.tagFields}><ProfileTagEditor label="기술 스택 태그" options={stackOptions} value={form.tags} onChange={value=>edit('tags',value)}/><ProfileTagEditor label="관심 분야" options={interestOptions.slice(1)} value={form.interests||[]} onChange={value=>edit('interests',value)}/></fieldset><div className={[styles.scope, "sns-upload"].join(" ")}><div className={styles.uploadHeading}><span>이미지 <span className={styles.imageCount}>{form.images.length}/{imagePolicy.maxCount}</span></span><button type="button" className={styles.addImage} aria-label="이미지 추가" title="이미지 추가" disabled={busy || form.images.length>=imagePolicy.maxCount} onClick={()=>imageInput.current?.click()}><Plus size={20}/></button></div><input ref={imageInput} hidden id={inputId} type="file" accept={imagePolicy.types.join(',')} multiple disabled={busy} onChange={async (e) => { const input = e.currentTarget; const files = Array.from(input.files || []); if (!files.length)
    return; if (form.images.length + files.length > imagePolicy.maxCount || files.some(f => !imagePolicy.types.includes(f.type) || f.size <= 0 || f.size > imagePolicy.maxBytes)) {
    setErrors(['이미지는 PNG/JPEG/WebP/GIF, 각 512KB 이하, 최대 3장까지 첨부해 주세요.']);
    input.value = '';
    return;
} setBusy(true); try {
    const images = await Promise.all(files.map(readImage));
    setForm(prev => ({ ...prev, images: [...prev.images, ...images] }));
    setErrors([]);
}
catch (err) {
    setErrors([err instanceof Error ? err.message : '첨부하지 못했습니다.']);
}
finally {
    setBusy(false);
    input.value = '';
} }}/><p className={[styles.scope, "auth-hint"].join(" ")}>최대 3장 · 각 512KB 이하 · 이미지는 브라우저에만 저장합니다.</p><div className={[styles.scope, "sns-image-previews"].join(" ")}>{!form.images.length && <button type="button" className={styles.emptyImage} aria-label="첫 이미지 추가" disabled={busy} onClick={()=>imageInput.current?.click()}><Image size={30}/><span>이미지로 이야기를 더해 보세요</span></button>}{form.images.map(i => <div className={styles.preview} key={i.id}><img src={i.url} alt={i.name}/><button type="button" className={styles.removeImage} aria-label={`${i.name} 이미지 제거`} disabled={busy} onClick={() => edit('images', form.images.filter(v => v.id !== i.id))}><X size={15}/></button></div>)}</div></div>{errors.length > 0 && <ul className={[styles.scope, "form-error"].join(" ")} role="alert">{errors.map(err => <li key={err}>{err}</li>)}</ul>}<div className={[styles.scope, "modal-actions"].join(" ")}><Button type="button" variant="secondary" disabled={busy} onClick={() => navigate(post ? `/feeds/${post.id}` : user ? `/users/${user.id}?tab=posts` : '/login')}>취소</Button><Button type="submit" disabled={busy}>{busy ? '이미지 읽는 중…' : post ? '수정 저장' : '게시글 등록'}</Button></div></form>; }
