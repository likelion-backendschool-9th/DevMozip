import {describe,it,expect} from 'vitest';
import {projectRoleCode,projectRoleLabel,normalizeProjectChoice,validProjectChoice} from './projectChoices';
import {validateProjectInput} from './projectValidation';
import {emptyProjectInput} from '@/mocks/project';
describe('프로젝트 후보와 직접 입력',()=>{
 it('기본 직군 코드와 라벨의 호환성을 유지한다',()=>{expect(projectRoleCode('프론트엔드')).toBe('FRONTEND');expect(projectRoleCode('backend')).toBe('BACKEND');expect(projectRoleCode('디자이너')).toBe('DESIGNER');expect(projectRoleLabel('BACKEND')).toBe('백엔드');});
 it('직접 입력과 전각·공백을 처리하고 객체 속성 이름도 문자열로 표시한다',()=>{expect(projectRoleCode(' ＳＲＥ ')).toBe('SRE');expect(projectRoleLabel('constructor')).toBe('constructor');expect(normalizeProjectChoice(' 보안  엔지니어 ')).toBe('보안 엔지니어');expect(validProjectChoice('x\ny')).toBe(false);expect(validProjectChoice('x'.repeat(31))).toBe(false);});
 it('Mock 작성에서도 세 가지 직접 입력을 허용한다',()=>{const input={...emptyProjectInput,title:'직접 입력',summary:'직접 입력 설명',description:'직접 입력 항목을 검증하는 프로젝트',goal:'검증',field:'공공서비스',stacks:['Rust'],developmentStart:'2026-11-10',developmentEnd:'2026-12-31',recruitmentStart:'2026-11-01',deadline:'2026-11-10',slots:[{role:'SRE',count:1,minYears:0}]};expect(validateProjectInput(input,new Date('2026-11-06'))).toEqual([]);expect(validateProjectInput({...input,slots:[...input.slots,{role:'sre',count:1,minYears:0}]},new Date('2026-11-06')).length).toBeGreaterThan(0);});
});
