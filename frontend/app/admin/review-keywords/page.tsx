'use client';
import { useState } from 'react';
import { adminStyles as styles } from '../_components/AdminShell';
import { useAuth } from '@/lib/state/authStore';
import { Button } from '@/components/common/Button';

export default function AdminReviewKeywordsPage() {
    const { administration, administer } = useAuth();
    const [keyword, setKeyword] = useState('');

    return (
        <section className={[styles.scope, 'governance-panel admin-main'].join(' ')} aria-label="리뷰 키워드 관리">
            <h2>리뷰 키워드 관리</h2>
            <p>삭제하면 새 리뷰의 선택지에서 제외됩니다. 이미 작성된 리뷰와 버블 통계는 유지합니다.</p>
            <form
                className={[styles.scope, 'governance-actions'].join(' ')}
                onSubmit={e => {
                    e.preventDefault();
                    if (administer({ type: 'keyword-add', keyword })) setKeyword('');
                }}
            >
                <label>
                    새 키워드
                    <input
                        className={[styles.scope, 'form-input'].join(' ')}
                        maxLength={30}
                        value={keyword}
                        onChange={e => setKeyword(e.target.value)}
                    />
                </label>
                <Button type="submit">추가</Button>
            </form>
            {administration.keywords.map(k => (
                <div className={[styles.scope, 'admin-row'].join(' ')} key={k}>
                    <span>{k}</span>
                    <Button variant="secondary" onClick={() => administer({ type: 'keyword-remove', keyword: k })}>
                        선택지에서 삭제
                    </Button>
                </div>
            ))}
        </section>
    );
}
