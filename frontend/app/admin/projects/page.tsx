'use client';
import { adminStyles as styles } from '../_components/AdminShell';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { ApiAdminProjects } from './_components/ApiAdminProjects';

// WBS 14.4·14.5. Mock에는 공고 관리 화면이 없어 토글 ON은 안내만 보여 준다 (docs/admin-plan.md 6.2).
export default function AdminProjectsPage() {
    const { enabled } = useMockVisibility();
    if (!enabled) return <ApiAdminProjects />;
    return (
        <section className={[styles.scope, 'governance-panel admin-main'].join(' ')} aria-label="프로젝트 공고 관리">
            <h2>프로젝트 공고 관리</h2>
            <p>Mock 데이터에는 공고 관리 기능이 없습니다. 상단의 Mock 토글을 끄면 서버의 공고를 숨기거나 삭제할 수 있습니다.</p>
        </section>
    );
}
