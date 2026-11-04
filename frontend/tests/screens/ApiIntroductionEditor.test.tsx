import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ApiIntroductionEditor } from '@/app/(user)/users/[userId]/_components/ApiIntroductionEditor';
import { ApiProfileIntroduction } from '@/app/(user)/users/[userId]/_components/ApiProfileIntroduction';
describe('API 상세 자기소개 화면', () => {
    it('기존 편집기의 형식·작성·미리보기·글자 수·삭제 안내를 유지한다', () => {
        const html = renderToStaticMarkup(<ApiIntroductionEditor initial={{ introduction: '저장된 소개', format: 'html' }} onClose={() => {}} onSaved={() => {}} />);
        for (const text of ['상세 소개 수정', '일반 텍스트', 'HTML', '작성', '미리보기', '20,000', '저장된 소개', '내용을 비우고 저장하면', '저장하기']) expect(html).toContain(text);
        expect(html).toContain('소제목');
        expect(html).not.toContain('한 줄 소개');
    });
    it('조회 중에는 미작성 상태나 편집 버튼을 섣불리 표시하지 않는다', () => {
        const html = renderToStaticMarkup(<ApiProfileIntroduction nickname="회원" slug="member-tag" canEdit={true} />);
        expect(html).toContain('ABOUT ME');
        expect(html).toContain('자기소개를 불러오고 있어요');
        expect(html).not.toContain('아직 상세 소개를 작성하지 않았습니다.');
        expect(html).not.toContain('>수정</button>');
    });
});
