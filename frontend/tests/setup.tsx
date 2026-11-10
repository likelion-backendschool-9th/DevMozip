import {beforeEach,vi} from 'vitest';
import {mockVisibilityStore} from '@/lib/demo/mockVisibility';
import {createElement} from 'react';
import {fixture} from './helpers/RouterFixture';
vi.mock('next/navigation',()=>({
 usePathname:()=>new URL(fixture.url,'http://localhost').pathname,
 useSearchParams:()=>new URLSearchParams(new URL(fixture.url,'http://localhost').search),
 useParams:()=>fixture.params,
 useRouter:()=>({push:(url:string)=>{fixture.url=url;},replace:(url:string)=>{fixture.url=url;},back:()=>{},forward:()=>{}})
}));
vi.mock('next/link',()=>({default:({href,children,prefetch,replace,scroll,...props}:Record<string,unknown>)=>createElement('a',{...props,href},children as never)}));

// 기존 프로토타입 회귀 테스트는 시연 모드를 명시적으로 켠다.
// 제품의 기본값/서버 스냅샷은 mockVisibility.test.ts에서 실제 구현으로 별도 검증한다.
beforeEach(() => { mockVisibilityStore.setEnabled(true); });
vi.mock('@/lib/state/mockVisibilityStore', async importOriginal => {
 const original = await importOriginal<typeof import('@/lib/state/mockVisibilityStore')>();
 return {...original, useMockVisibility: () => ({enabled: mockVisibilityStore.getSnapshot(), setEnabled: (enabled: boolean) => mockVisibilityStore.setEnabled(enabled)})};
});

// vi.resetModules()로 서비스를 다시 읽는 테스트도 동일한 시연 fixture를 사용한다.
vi.mock('@/lib/demo/mockVisibility', async importOriginal => {
 const original = await importOriginal<typeof import('@/lib/demo/mockVisibility')>();
 original.mockVisibilityStore.setEnabled(true);
 return original;
});
