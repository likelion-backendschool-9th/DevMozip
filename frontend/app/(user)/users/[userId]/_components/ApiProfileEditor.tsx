'use client';
import { useEffect, useRef, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { Modal } from '@/components/common/Modal';
import { Input } from '@/components/common/Input';
import { Button } from '@/components/common/Button';
import { ProfileImageEditor } from './ProfileImageEditor';
import { notify } from '@/lib/state/prototypeStore';
import { changeOwnImage, fetchJobCategories, saveOwnProfile } from '@/lib/profiles/profileUpdate';
import { useOwnProfile } from '../../../_components/OwnProfileProvider';
import type { ProfileDetail, ProfileImageKind } from '@/types/profileDetail';
import styles from './ProfileEditor.module.css';

export function ApiProfileEditor({ profile, onClose, onSaved }: { profile: ProfileDetail; onClose: () => void; onSaved: (profile: ProfileDetail) => void }) {
    const own = useOwnProfile();
    const router = useRouter();
    const pathname = usePathname();
    const [nickname, setNickname] = useState(profile.nickname);
    const [tag, setTag] = useState(profile.tag);
    const [summary, setSummary] = useState(profile.summary || '');
    const [career, setCareer] = useState(profile.career === null ? '' : String(profile.career));
    const [jobId, setJobId] = useState(profile.jobCategory.id === null ? '' : String(profile.jobCategory.id));
    const [contact, setContact] = useState(profile.contactHours || '');
    const [collaboration, setCollaboration] = useState(profile.collaborationStatus || '');
    const [jobs, setJobs] = useState<{ id: number; name: string }[]>([]);
    const [jobsError, setJobsError] = useState('');
    const [attempt, setAttempt] = useState(0);
    const [error, setError] = useState('');
    const [saving, setSaving] = useState(false);
    const [imageBusy, setImageBusy] = useState(0);
    const imageProfile = useRef(profile);
    useEffect(() => {
        const controller = new AbortController();
        setJobsError('');
        void fetchJobCategories(controller.signal).then(setJobs).catch(error => {
            if (!controller.signal.aborted) setJobsError(error instanceof Error ? error.message : '직군 목록을 불러오지 못했어요.');
        });
        return () => controller.abort();
    }, [attempt]);
    async function image(kind: ProfileImageKind, file: File | null) {
        const result = await changeOwnImage(kind, file);
        // 기본 정보 편집값과 이미지 저장 결과를 분리해 미저장 텍스트를 유지한다.
        const changed = { ...imageProfile.current, ...result };
        imageProfile.current = changed;
        own.update(changed);
        onSaved(changed);
    }
    const busy = saving || imageBusy > 0;
    return <Modal className={`${styles.dialog} profile-dialog`} title="기본 정보 수정" onClose={() => { if (!busy) onClose(); }}>
        <form className="profile-form" onSubmit={async event => {
            event.preventDefault();
            if (busy) return;
            setError('');
            if (!/^\d+$/.test(career) && career !== '') { setError('경력은 0 이상의 개월 수로 입력해 주세요.'); return; }
            if (!Number.isSafeInteger(Number(career)) || Number(career) > 2147483647) { setError('경력 개월 수를 확인해 주세요.'); return; }
            if (!jobId || !jobs.some(job => String(job.id) === jobId)) { setError('직군을 선택해 주세요.'); return; }
            setSaving(true);
            try {
                const saved = await saveOwnProfile({ nickname, tag, jobCategoryId: Number(jobId), summary,
                    career: career === '' ? null : Number(career), contactHours: contact, collaborationStatus: collaboration || null });
                own.update(saved); onSaved(saved); onClose(); notify('프로필을 저장했어요.');
                if (pathname.startsWith('/users/') && saved.tag !== profile.tag) router.replace(`/users/${encodeURIComponent(saved.tag)}`);
            } catch (error) { setError(error instanceof Error ? error.message : '저장하지 못했어요.'); }
            finally { setSaving(false); }
        }}>
            <div className={styles.imageCover}>
                <ProfileImageEditor label="배너 이미지" banner value={profile.bannerImage || ''} name={nickname} color="lavender" disabled={busy} onChange={() => {}} onBusy={value => setImageBusy(count => count + (value ? 1 : -1))} onFileChange={file => image('banner', file)} onDelete={() => image('banner', null)} />
                <ProfileImageEditor label="프로필 이미지" value={profile.profileImage || ''} name={nickname} color="lavender" disabled={busy} onChange={() => {}} onBusy={value => setImageBusy(count => count + (value ? 1 : -1))} onFileChange={file => image('profile', file)} onDelete={() => image('profile', null)} />
            </div>
            <p className="auth-hint">이미지는 변경·삭제 즉시 저장돼요. PNG · JPEG · WebP / 각각 256KB 이하</p>
            <fieldset disabled={busy} style={{ border: 0, padding: 0, margin: 0, minWidth: 0, display: 'contents' }}>
                <div className="auth-two-columns">
                    <label>닉네임<Input required minLength={2} maxLength={20} value={nickname} onChange={e => setNickname(e.target.value)} /></label>
                    <label>태그<Input required minLength={2} maxLength={30} pattern="[a-zA-Z0-9_-]+" value={tag} onChange={e => setTag(e.target.value)} /></label>
                </div>
                <p className="auth-hint">닉네임 2~20자 · 태그 2~30자, 영문·숫자·밑줄·하이픈. @ 없이 입력해 주세요.</p>
                <label>한 줄 소개<Input maxLength={150} value={summary} onChange={e => setSummary(e.target.value)} /></label>
                <div className="auth-two-columns">
                    <label>직군<select required className="form-input" value={jobId} onChange={e => setJobId(e.target.value)}><option value="">직군 선택</option>{jobs.map(job => <option key={job.id} value={job.id}>{job.name}</option>)}</select></label>
                    <label>경력(개월 수)<Input inputMode="numeric" pattern="[0-9]*" value={career} onChange={e => setCareer(e.target.value)} placeholder="예: 18" /></label>
                </div>
                <p className="auth-hint">경력 0은 신입, 빈칸은 미입력이에요.</p>
                {jobsError && <p role="alert" className="form-error">{jobsError} <button type="button" onClick={() => setAttempt(v => v + 1)}>다시 시도</button></p>}
                {!jobsError && jobs.length === 0 && <p role="status">선택할 직군이 없거나 목록을 불러오는 중이에요.</p>}
                <label>연락 가능 시간<Input maxLength={100} value={contact} onChange={e => setContact(e.target.value)} /></label>
                <label className="checkbox-label"><input type="checkbox" checked={collaboration === 'AVAILABLE'} onChange={e => setCollaboration(e.target.checked ? 'AVAILABLE' : 'UNAVAILABLE')} />새로운 프로젝트에 협업할 수 있어요.</label>
            </fieldset>
            {error && <p role="alert" className="form-error">{error}</p>}
            <div className="modal-actions"><Button type="button" variant="secondary" disabled={busy} onClick={onClose}>취소</Button><Button type="submit" disabled={busy || jobs.length === 0}>{saving ? '저장 중…' : '저장하기'}</Button></div>
        </form>
    </Modal>;
}
