import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
vi.mock('@/app/(user)/_components/OwnProfileProvider', () => ({ useOwnProfile: vi.fn() }));
import { useOwnProfile } from '@/app/(user)/_components/OwnProfileProvider';
import { HeaderProfileLink } from '@/app/_components/HeaderProfileLink';
import { apiProfileFixture } from '@/tests/helpers/apiProfileFixture';
const render = () => renderToStaticMarkup(<HeaderProfileLink />);
function state(profile: typeof apiProfileFixture | null, loading = false) {
    vi.mocked(useOwnProfile).mockReturnValue({ profile, loading, error: '', retry: () => {}, update: () => {} });
}
afterEach(() => vi.clearAllMocks());
describe('헤더의 실제 회원 프로필', () => {
    it('내 프로필의 닉네임과 원형 썸네일을 사용한다', () => {
        state({ ...apiProfileFixture, nickname: '실제회원', profileImage: 'http://localhost:8080/api/v1/profile-images/avatar.png' });
        const html = render();
        expect(html).toContain('title="실제회원님의 프로필"');
        expect(html).toContain('alt="실제회원 프로필 이미지"');
        expect(html).toContain('src="http://localhost:8080/api/v1/profile-images/avatar.png"');
        expect(html).toContain('href="/profile/me"');
        expect(html).not.toContain('김하늘');
    });
    it('사진이 없으면 실제 닉네임 앞 두 글자를 표시한다', () => {
        state({ ...apiProfileFixture, nickname: '수정닉네임', profileImage: null });
        const html = render();
        expect(html).toContain('>수정</span>');
        expect(html).toContain('수정닉네임님의 프로필');
        expect(html).not.toContain('<img');
    });
    it.each([true, false])('내 프로필 데이터가 없을 때 데모 이름을 표시하지 않는다 (로딩: %s)', loading => {
        state(null, loading);
        const html = render();
        expect(html).toContain('title="내 프로필로 이동"');
        expect(html).toContain('lucide-user-round');
        expect(html).not.toContain('김하');
        expect(html.includes('aria-busy="true"')).toBe(loading);
    });
    it('유효하지 않은 이미지 URL에는 닉네임을 사용한다', () => {
        state({ ...apiProfileFixture, nickname: '안전회원', profileImage: 'javascript:alert(1)' });
        const html = render();
        expect(html).toContain('>안전</span>');
        expect(html).not.toContain('javascript:');
    });
});
