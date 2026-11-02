import { projectFields, recruitmentRoles } from '@/mocks/project';
import { stackOptions } from '@/mocks/home';
import type { CreateProjectInput } from '@/types/project';
function validDate(value:string){if(!/^\d{4}-\d{2}-\d{2}$/.test(value))return false;const date=new Date(`${value}T00:00:00Z`);return !Number.isNaN(date.getTime())&&date.toISOString().slice(0,10)===value;}
export function validateProjectInput(input:CreateProjectInput,now=new Date()){
 const errors:string[]=[];
 if(!input.title.trim()||input.title.length>100)errors.push('프로젝트명은 1~100자로 입력해 주세요.');
 if(!input.summary.trim()||input.summary.length>200)errors.push('한 줄 소개는 1~200자로 입력해 주세요.');
 if(input.description.trim().length<10||input.description.length>5000)errors.push('프로젝트 소개는 10~5,000자로 입력해 주세요.');
 if(!input.goal.trim()||input.goal.length>1000)errors.push('목표는 1~1,000자로 입력해 주세요.');
 if(!projectFields.includes(input.field)||!input.stacks.length||input.stacks.some(s=>!stackOptions.includes(s)))errors.push('분야와 기술 스택을 올바르게 선택해 주세요.');
 const dates=[input.developmentStart,input.developmentEnd,input.recruitmentStart,input.deadline];
 if(dates.some(d=>!validDate(d))||input.developmentEnd<input.developmentStart||input.deadline<input.recruitmentStart||input.deadline>input.developmentEnd||new Date(`${input.deadline}T23:59:59.999+09:00`).getTime()<now.getTime())errors.push('개발·모집 기간과 마감일을 확인해 주세요.');
 if(!input.slots.length||new Set(input.slots.map(s=>s.role)).size!==input.slots.length||input.slots.some(s=>!recruitmentRoles.includes(s.role)||!Number.isInteger(s.count)||s.count<1||s.count>10||!Number.isInteger(s.minYears)||s.minYears<0||s.minYears>5))errors.push('중복 없이 모집 직군과 인원(1~10명), 최소 경력(0~5년)을 입력해 주세요.');
 return errors;
}
