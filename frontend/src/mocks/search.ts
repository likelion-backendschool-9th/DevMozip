import { projectFields, recruitmentRoles } from './project';
export const projectRoleOptions=recruitmentRoles;
export const projectFieldOptions=projectFields;
export const projectStatusOptions=['모집 중','모집 마감','모집 완료','프로젝트 진행 중','프로젝트 종료'];
export const availabilityOptions=[{value:'',label:'전체 상태'},{value:'yes',label:'협업 가능'},{value:'no',label:'협업 쉬는 중'}];
export const searchTargetOptions=[{value:'developer',label:'개발자'},{value:'project',label:'프로젝트'}] as const;
