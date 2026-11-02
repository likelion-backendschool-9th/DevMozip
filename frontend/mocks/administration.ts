import { reviewKeywords } from '@/mocks/profile';
import type { AdministrationState } from '@/types/administration';
export const initialAdministration:AdministrationState={version:1,subscriptions:{},members:{},resolutions:{},keywords:[...reviewKeywords],audit:[],payments:[{id:'example-payment-1',userId:'demo-premium',date:'2026-10-01',amount:9900,status:'결제 완료 예시'}]};
export const planFeatures=[['AI 개발자 추천','월 15회','월 100회 (임시 정책)'],['포트폴리오','요약 정보','상세 열람 + AI 피드백 데모'],['개발자 검색','기존 기본 필터','기본 필터 + 신뢰 온도 조건·정렬'],['추천 이유','적합도 퍼센트','상세 추천 이유'],['프로젝트','조회·지원','조회·지원·모집 공고 작성'],['프로필 뱃지','일반 회원','PRO 뱃지']];
export const adminTabs=['대시보드','회원','프로젝트 신고','게시글·댓글','리뷰 키워드','신뢰 온도','AI 사용량','구독 현황'];
export const aiErrorExamples=[{id:'ai-example-1',date:'2026-10-28 14:20',code:'MOCK_LIMIT_REACHED',message:'무료 월 한도 도달 예시 · 실제 오류 수집 아님'},{id:'ai-example-2',date:'2026-10-27 10:03',code:'MOCK_INPUT_MISSING',message:'추가 정보 필요 예시 · 실제 외부 AI 요청 없음'}];
