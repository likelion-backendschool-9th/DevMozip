'use client';
import { useState, type FormEvent } from 'react';
import { Modal } from '@/components/common/Modal';
import { Button } from '@/components/common/Button';
import { updateFollowNotification } from '@/lib/profiles/followApi';

/**
 * 팔로우한 개발자의 활동 알림 설정(WBS 9.5). 체크박스 하나로 새 게시글·새 프로젝트 모집 알림을 함께 켜고 끈다.
 * Mock 화면의 FollowSettings는 그대로 둔다.
 */
export function ApiFollowSettings({ slug, name, enabled, onSaved, onClose }: {
    slug: string;
    name: string;
    enabled: boolean;
    onSaved: (enabled: boolean) => void;
    onClose: () => void;
}) {
    const [value, setValue] = useState(enabled);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    async function save(event: FormEvent) {
        event.preventDefault();
        if (saving) return;
        setSaving(true);
        setError('');
        try {
            const result = await updateFollowNotification(slug, value);
            onSaved(result.activityNotificationEnabled);
            onClose();
        } catch (failure) {
            setError(failure instanceof Error ? failure.message : '알림 설정을 저장하지 못했어요.');
        } finally {
            setSaving(false);
        }
    }
    return <Modal title="팔로잉 개발자 알림 설정" onClose={onClose}>
        <p>{name}님의 새로운 소식을 받을지 선택해 주세요.</p>
        <form className="profile-form" onSubmit={event => void save(event)}>
            <label className="checkbox-label">
                <input type="checkbox" checked={value} onChange={event => setValue(event.target.checked)} />
                활동 알림 (새 게시글·새 프로젝트 모집)
            </label>
            <p className="auth-hint">설정은 바로 저장되고, 알림 기능이 연결되면 그때부터 적용돼요. 언팔로우하면 설정이 해제됩니다.</p>
            {error && <p role="alert">{error}</p>}
            <div className="modal-actions">
                <Button type="button" variant="secondary" onClick={onClose}>취소</Button>
                <Button type="submit" disabled={saving}>{saving ? '저장 중…' : '설정 저장'}</Button>
            </div>
        </form>
    </Modal>;
}
