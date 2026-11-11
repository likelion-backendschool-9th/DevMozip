import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { KeywordListView } from '@/app/admin/review-keywords/_components/ApiAdminReviewKeywords';
import { activeNames, keywordName } from '@/lib/administration/adminKeywords';

const keywords = [{ id: 1, name: '소통이 원활해요', active: true }, { id: 2, name: '함께 성장해요', active: false }];

describe('서버 리뷰 키워드 관리 화면 (14.9)', () => {
    it('저장 전 목록에서 새 키워드와 다시 추가한 키워드를 표시한다', () => {
        const html = renderToStaticMarkup(<KeywordListView keywords={keywords} names={['소통이 원활해요', '함께 성장해요', '코드 리뷰']} onRemove={() => {}} />);
        expect(html).toContain('소통이 원활해요');
        expect(html).toContain('다시 추가 · 저장 전');
        expect(html).toContain('새 키워드 · 저장 전');
        expect(html.match(/선택지에서 삭제/g)?.length).toBe(3);
    });
    it('빈 선택지를 안내한다', () => {
        expect(renderToStaticMarkup(<KeywordListView keywords={keywords} names={[]} onRemove={() => {}} />)).toContain('선택지가 비어 있습니다.');
    });
    it('활성 키워드만 처음 목록으로 쓰고, 이름 규칙을 확인한다', () => {
        expect(activeNames(keywords)).toEqual(['소통이 원활해요']);
        expect(keywordName('  코드 리뷰 ')).toBe('코드 리뷰');
        expect(keywordName('   ')).toBeNull();
        expect(keywordName('가'.repeat(31))).toBeNull();
    });
});
