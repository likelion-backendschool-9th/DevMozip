import { describe,it,expect } from 'vitest';
import { filterPosts, freshHomeState, parseHomeState, recommend, sortProjects } from './homeService';
describe('메인 Mock 서비스',()=>{
 it('기술과 관심 분야를 반영해 추천 순위가 바뀐다',()=>{
 const a=recommend({description:'생산성 일정 관리 도구를 만들어요',stacks:['React','TypeScript','Figma'],interest:'생산성'});
 const b=recommend({description:'AI 학습 도우미를 만들고 싶어요',stacks:['Python'],interest:'AI'});
 expect(a.developers[0].id).toBe('yuna');expect(b.developers[0].id).toBe('seoyeon');expect(a.projects[0].id).toBe('flowmate');expect(b.projects[0].id).toBe('learnloop');
 expect(a.developers.every(d=>d.score<=98&&d.score>=55)).toBe(true);
 });
 it('공고의 세 정렬 방식이 서로 다른 결과를 반환한다',()=>{expect(sortProjects('latest')[0].id).toBe('learnloop');expect(sortProjects('popular')[0].id).toBe('pocket');expect(sortProjects('deadline')[0].id).toBe('pocket');});
 it('카테고리와 팔로잉 조건을 함께 적용한다',()=>{const list=filterPosts('following','정보',['yuna'],true);expect(list.length).toBe(2);expect(list.every(p=>p.authorId==='yuna'&&p.category==='정보')).toBe(true);expect(filterPosts('following','전체',['yuna'],false)).toEqual([]);expect(filterPosts('following','홍보',['yuna'],true)).toEqual([]);});
 it('정렬이 원본 데이터를 바꾸지 않는다',()=>{sortProjects('popular');expect(sortProjects('latest')[0].id).toBe('learnloop');});
 it('데모 상태를 복원하고 손상된 상태는 초기화한다',()=>{const state=freshHomeState();state.input.description='데모 프로젝트 설명';expect(parseHomeState(JSON.stringify(state))).toEqual(state);expect(parseHomeState('{broken')).toEqual(freshHomeState());expect(parseHomeState('{"version":1,"role":"admin"}')).toEqual(freshHomeState());});
 it('초기 상태는 서로 참조를 공유하지 않는다',()=>{const a=freshHomeState();a.input.stacks.push('React');expect(freshHomeState().input.stacks).toEqual([]);});
});
