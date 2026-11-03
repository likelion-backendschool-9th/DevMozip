import {describe,it,expect} from 'vitest';
import {upgradePortfolioMock} from './portfolioMockMigration';
import {freshProfileState,parseProfileState} from './profileService';
import {portfolioLegacyCopy} from '@/mocks/portfolioLegacyCopy';
import {portfolioDetailFields} from '@/mocks/profile';
describe('실사용 형식 Mock 포트폴리오',()=>{
 it('프로젝트 설명과 기여는 독립 섹션과 다른 내용이다',()=>{const item=freshProfileState().portfolios[0];expect(portfolioDetailFields.map(f=>f.key)).toEqual(['description','contribution','features','challenge','outcome']);expect(item.description).not.toEqual(item.contribution);expect(item.description.length).toBeGreaterThan(150);expect(item.contribution?.length).toBeGreaterThan(150);});
 it('저장된 이전 예시 문구만 갱신하고 반복 적용해도 유지한다',()=>{const state=freshProfileState();const item=state.portfolios[0];const legacy={...item,...portfolioLegacyCopy[item.id]};const upgraded=upgradePortfolioMock(legacy);expect(upgraded.description).toEqual(item.description);expect(upgraded.contribution).toEqual(item.contribution);expect(upgradePortfolioMock(upgraded)).toEqual(upgraded);state.portfolios[0]=legacy;expect(parseProfileState(JSON.stringify(state)).portfolios[0].description).toEqual(item.description);});
 it('작성자가 고친 내용과 삭제한 이미지·링크는 보존한다',()=>{const item={...freshProfileState().portfolios[0],description:'내가 직접 고친 설명',contribution:'내 기여',images:[],links:[]};expect(upgradePortfolioMock(item)).toEqual(item);expect(upgradePortfolioMock({...item,id:'new-item'})).toEqual({...item,id:'new-item'});});
});
