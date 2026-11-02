import {describe,it,expect} from 'vitest';
import {canOpenPortfolioDetail,canUsePortfolioFeedback,freshProfileState,applyProfileAction,parseProfileState,portfolioErrors} from './profileService';
describe('포트폴리오 상세 분리',()=>{
 it('타인 상세는 프리미엄·관리자가 열람한다',()=>{expect(canOpenPortfolioDetail('free')).toBe(false);expect(canOpenPortfolioDetail()).toBe(false);expect(canOpenPortfolioDetail('premium')).toBe(true);expect(canOpenPortfolioDetail('admin')).toBe(true);});
 it('본인은 무료도 열람하고 타인·비로그인 소유자 일치는 차단한다',()=>{expect(canOpenPortfolioDetail('free','owner','owner')).toBe(true);expect(canOpenPortfolioDetail('free','owner','other')).toBe(false);expect(canOpenPortfolioDetail(undefined,'owner',null)).toBe(false);expect(canOpenPortfolioDetail()).toBe(false);});
 it('피드백은 프리미엄인 본인만 볼 수 있다',()=>{expect(canUsePortfolioFeedback('a','a','premium')).toBe(true);expect(canUsePortfolioFeedback('a','b','premium')).toBe(false);expect(canUsePortfolioFeedback('a','a','free')).toBe(false);expect(canUsePortfolioFeedback('a','b','admin')).toBe(false);});
 it('대표 이미지와 분야는 기존 데이터와 호환하여 저장·복원된다',()=>{const state=freshProfileState();const item=state.portfolios[0];const next=applyProfileAction(state,item.ownerId,{type:'portfolio-save',id:item.id,input:{...item,field:'웹 서비스',coverImage:''}});expect(parseProfileState(JSON.stringify(next)).portfolios[0]).toMatchObject({field:'웹 서비스',coverImage:''});expect(portfolioErrors({...item,coverImage:'javascript:alert(1)'}).length).toBeGreaterThan(0);expect(portfolioErrors({...item,field:'a'.repeat(41)}).length).toBeGreaterThan(0);});
});
