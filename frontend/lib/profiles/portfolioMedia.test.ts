import {describe,it,expect} from 'vitest';
import {portfolioMediaErrors,validPortfolioImage} from './portfolioMedia';
import {applyProfileAction,freshProfileState,parseProfileState} from './profileService';
import {portfolioDemoImages} from '@/mocks/portfolioShowcases';
const image='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jVZkAAAAASUVORK5CYII=';
describe('포트폴리오 이미지·링크',()=>{
 it('직접 추가한 이미지·설명·링크와 기여를 저장하고 복원한다',()=>{const s=freshProfileState();const item=s.portfolios[0];const input={...item,images:[{src:image,caption:'직접 구현한 화면'}],links:[{label:'GitHub',url:'https://github.com/example/project'}],teamSize:'3명',contribution:'폼 설계와 구현'};const next=applyProfileAction(s,item.ownerId,{type:'portfolio-save',id:item.id,input});expect(parseProfileState(JSON.stringify(next)).portfolios[0]).toMatchObject(input);});
 it('위험 URL·임의 로컬 이미지·용량 초과·개수 초과를 거절한다',()=>{expect(validPortfolioImage('javascript:alert(1)')).toBe(false);expect(validPortfolioImage('/arbitrary.svg')).toBe(false);expect(validPortfolioImage('https://example.com/a.png')).toBe(false);expect(validPortfolioImage(portfolioDemoImages[0])).toBe(true);expect(portfolioMediaErrors({images:Array(7).fill({src:image,caption:''})})).not.toEqual([]);expect(portfolioMediaErrors({images:[{src:'data:image/png;base64,'+'a'.repeat(400000),caption:''}]})).not.toEqual([]);for(const url of ['javascript:alert(1)','data:text/html,hi','//example.com'])expect(portfolioMediaErrors({links:[{label:'링크',url}]})).not.toEqual([]);});
 it('기존 저장 데이터에는 Mock 사례를 보완하고 명시적으로 삭제한 항목은 유지한다',()=>{const s=freshProfileState();const item=s.portfolios[0];delete item.images;delete item.links;expect(parseProfileState(JSON.stringify(s)).portfolios[0].images?.length).toBeGreaterThan(0);item.images=[];item.links=[];expect(parseProfileState(JSON.stringify(s)).portfolios[0]).toMatchObject({images:[],links:[]});});
 it('변조된 저장 링크를 복원하지 않는다',()=>{const s=freshProfileState();s.portfolios[0].links=[{label:'링크',url:'javascript:alert(1)'}];expect(parseProfileState(JSON.stringify(s)).portfolios[0].links).not.toEqual(s.portfolios[0].links);});
});
