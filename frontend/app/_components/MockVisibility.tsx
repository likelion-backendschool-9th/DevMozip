'use client';
import type { ReactNode } from 'react';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { usePrototype } from '@/lib/state/prototypeStore';
import styles from './MockVisibility.module.css';

export function MockVisibilityToggle() {
    const { enabled, setEnabled } = useMockVisibility();
    const { notify } = usePrototype();
    return <button className={styles.toggle} type="button" role="switch" aria-checked={enabled}
        aria-label={enabled ? 'Mock 데이터 끄기' : 'Mock 데이터 켜기'} onClick={() => {
            if (!setEnabled(!enabled)) notify('표시 설정을 저장하지 못해 현재 탭에만 적용했습니다.');
        }}><span className={styles.track} data-enabled={enabled}><span /></span>Mock 데이터 {enabled ? '끄기' : '켜기'}</button>;
}

/** 데이터 모드가 바뀌면 같은 화면을 다시 마운트한다. 페이지·폼·탭은 숨기지 않는다. */
export function MockDataWorkspace({ children }: { children: ReactNode }) {
    const { enabled } = useMockVisibility();
    return <div key={enabled ? 'demo' : 'empty'} style={{ display: 'contents' }}>{children}</div>;
}

export function MockDataNote() {
    const { enabled } = useMockVisibility();
    return <>{enabled ? '기획 발표용 Mock 프로토타입 · 추천 점수와 콘텐츠는 데모 데이터입니다.' : '프로젝트는 실제 DB·API로 연결됩니다. 다른 미연결 영역은 샘플 없이 표시됩니다.'}</>;
}
