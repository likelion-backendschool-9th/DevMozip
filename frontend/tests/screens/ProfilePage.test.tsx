import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter, Route, Routes } from '@/tests/helpers/RouterFixture';
import { ProfilePage } from '@/app/users/[userId]/_components/ProfileScreen';
import { AppLayout } from '@/components/layout/AppLayout';
import { PrototypeProvider } from '@/lib/state/PrototypeProvider';
import { AuthProvider } from '@/lib/state/AuthProvider';
import { ProfileProvider } from '@/lib/state/ProfileProvider';
import { ProjectProvider } from '@/lib/state/ProjectProvider';
import { MessagingProvider } from '@/lib/state/MessagingProvider';
import { HomeProvider } from '@/lib/state/HomeProvider';
import { SESSION_KEY } from '@/lib/account/authService';
import { profileTabs } from '@/mocks/profile';
function page(url:string,userId?:string){
 const data=new Map<string,string>();if(userId)data.set(SESSION_KEY,JSON.stringify({version:1,userId}));
 vi.stubGlobal('localStorage',{getItem:(key:string)=>data.get(key)||null});vi.stubGlobal('sessionStorage',{getItem:()=>null});
 return renderToStaticMarkup(<PrototypeProvider><AuthProvider><ProfileProvider><ProjectProvider><MessagingProvider><HomeProvider><MemoryRouter initialEntries={[url]}><Routes><Route element={<AppLayout/>}><Route path="/users/:userId" element={<ProfilePage/>}/></Route></Routes></MemoryRouter></HomeProvider></MessagingProvider></ProjectProvider></ProfileProvider></AuthProvider></PrototypeProvider>);
}
afterEach(()=>vi.unstubAllGlobals());
describe('프로필 화면 구성 통합 렌더링',()=>{
 it.each(profileTabs)('$label 탭에서도 상단 프로필과 5개 가로 탭이 유지된다',tab=>{const html=page(`/users/yuna?tab=${tab.id}`);expect(html).toContain('프로필 배너');expect(html).toContain('이유나');expect(html).toContain('@');expect(html).toContain('신뢰 온도');expect((html.match(/role="tab"/g)||[])).toHaveLength(5);expect(html).toContain(`id="profile-panel-${tab.id}"`);});
 it('일반 회원은 타인 상세 잠금, 프리미엄은 상세를 렌더링한다',()=>{expect(page('/users/yuna?tab=portfolio','demo-user')).toContain('더 깊은 이야기는 프리미엄에서');const premium=page('/users/yuna?tab=portfolio','demo-premium');expect(premium).toContain('PREMIUM DETAILS');expect(premium).not.toContain('더 깊은 이야기는 프리미엄에서');});
 it('본인 프로필에서는 CRUD 버튼과 본인 상세를 표시한다',()=>{const html=page('/users/demo-user?tab=portfolio','demo-user');expect(html).toContain('포트폴리오 등록');expect(html).toContain('내 포트폴리오 상세');expect(html).toContain('프로필 수정');});
 it('원본 작성자와 원본 경로를 퍼간 글에 표시한다',()=>{const html=page('/users/demo-user?tab=posts','demo-user');expect(html).toContain('원본 작성자 · 이유나');expect(html).toContain('/posts/react-patterns');expect(html).toContain('퍼가기 취소');});
 it('받은 리뷰와 버블 차트를 조회하고 직접 작성 폼은 제공하지 않는다',()=>{const html=page('/users/yuna?tab=reviews','demo-user');expect(html).toContain('리뷰 키워드 버블 차트');expect((html.match(/role="listitem"/g)||[])).toHaveLength(4);expect(html).toContain('조회 전용');expect(html).not.toContain('name="review-rating"');expect(html).not.toContain('협업 리뷰 등록');expect(html).not.toContain('이 프로젝트에서 함께 협업한 기록이 있어야 합니다.');});
 it.each(['__proto__','constructor'])('특수 경로 %s도 존재하지 않는 프로필로 처리한다',id=>{expect(page(`/users/${id}`)).toContain('프로필을 찾을 수 없어요.');});
 it('존재하지 않는 프로필은 안내를 표시하고, 잘못된 탭은 소개로 복원한다',()=>{expect(page('/users/missing')).toContain('프로필을 찾을 수 없어요.');expect(page('/users/yuna?tab=invalid')).toContain('id="profile-panel-about"');});
});
