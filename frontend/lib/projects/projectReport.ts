import {reportReasons} from '@/mocks/project';
export const PROJECT_REPORT_DETAIL_LIMIT=1000;
export function projectReportError(reason:unknown,detail:unknown){
 if(typeof reason!=='string'||!reportReasons.includes(reason))return '신고 사유를 선택해 주세요.';
 if(typeof detail!=='string'||detail.length>PROJECT_REPORT_DETAIL_LIMIT)return '추가 설명은 1,000자 이내로 작성해 주세요.';
 return '';
}
