import {validAvatar} from '@/lib/account/onboardingService';
import {safeExternalUrl} from '@/lib/utils/externalUrl';
import {portfolioDemoImages} from '@/mocks/portfolioShowcases';
import type {PortfolioInput} from '@/types/profile';
export function validPortfolioImage(src:unknown):src is string{return typeof src==='string'&&!!src&&(validAvatar(src)||(portfolioDemoImages as readonly string[]).includes(src));}
export function portfolioMediaErrors(input:Pick<PortfolioInput,'images'|'links'|'teamSize'|'contribution'|'fields'>){const errors:string[]=[];if(input.fields!==undefined&&(!Array.isArray(input.fields)||input.fields.length>30||input.fields.some(f=>typeof f!=='string'||!f.trim()||f.length>40)||new Set(input.fields.map(f=>f.toLowerCase())).size!==input.fields.length))errors.push('분야는 중복 없이 최대 30개, 항목별 40자 이내로 선택해 주세요.');
 if(input.teamSize!==undefined&&(typeof input.teamSize!=='string'||input.teamSize.length>100))errors.push('팀 구성은 100자 이내로 입력해 주세요.');
 if(input.contribution!==undefined&&(typeof input.contribution!=='string'||input.contribution.length>3000))errors.push('나의 기여는 3,000자 이내로 입력해 주세요.');
 if(input.images!==undefined&&(!Array.isArray(input.images)||input.images.length>6||input.images.some(image=>!image||!validPortfolioImage(image.src)||typeof image.caption!=='string'||image.caption.length>150)))errors.push('프로젝트 이미지는 PNG/JPEG/WebP 256KB 이하, 최대 6장이고 설명은 150자 이내입니다.');
 if(input.links!==undefined&&(!Array.isArray(input.links)||input.links.length>6||input.links.some(link=>!link||typeof link.label!=='string'||!link.label.trim()||link.label.length>40||typeof link.url!=='string'||link.url.length>500||!safeExternalUrl(link.url))))errors.push('관련 링크는 최대 6개, 이름 1~40자와 올바른 http/https URL을 입력해 주세요.');
 return errors;
}
