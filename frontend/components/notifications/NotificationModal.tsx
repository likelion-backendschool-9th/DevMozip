'use client';
import styles from './NotificationList.module.css';
import Link from 'next/link';
import { Modal } from '@/components/common/Modal';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { MockNotificationList } from './MockNotificationList';
import { NotificationList } from './NotificationList';

/**
 * 공통 알림 모달(WBS 12.1, D1 B). 헤더 종 버튼이 보던 화면 위에 열고 주소는 바꾸지 않는다.
 * 닫기·Esc·바깥 클릭으로 닫고, 알림을 눌러 다른 화면으로 이동하면 닫는다. 내용은 Mock 토글을 따른다.
 */
export function NotificationModal({ onClose }: { onClose: () => void }) {
    const { enabled } = useMockVisibility();
    return (
        <Modal title="알림" onClose={onClose} className={[styles.scope, 'notification-dialog'].join(' ')}>
            {enabled
                ? <MockNotificationList variant="modal" onNavigate={onClose} />
                : <NotificationList variant="modal" onNavigate={onClose} />}
            <div className={[styles.scope, 'notification-modal-footer'].join(' ')}>
                <Link href="/notifications" onClick={onClose}>전체 보기 →</Link>
            </div>
        </Modal>
    );
}
