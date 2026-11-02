import {portfolioBlockErrors,preparePortfolioBlocks,validPortfolioBlocks} from './portfolioBlocks';
import {portfolioMediaErrors} from './portfolioMedia';
import {upgradePortfolioMock} from './portfolioMockMigration';
import {validNickname} from '@/lib/account/authService';
import {sanitizeIntroduction} from './profileIntroduction';
import {introductionPolicy} from '@/mocks/profileIntroduction';
import {reviewPolicy} from '@/mocks/projectWorkspace';
import {onboardingErrors,sanitizeDraft,validAvatar} from '@/lib/account/onboardingService';
import { addActivityNotice } from '@/lib/social/activityNoticeService';
import { publishFollowEvent } from '@/lib/profiles/followService';
import { applySocialAction, cleanupPostSocial, legacySocial, parseSocial, validImage, validPostTags } from '@/lib/social/socialService';
import type { SocialAction } from '@/types/social';
import { safeExternalUrl } from '@/lib/utils/externalUrl';
export { safeExternalUrl } from '@/lib/utils/externalUrl';
import { createMockId } from '@/lib/utils/createMockId';
import { collaborations, initialProfileState, profileFromAccount, reviewKeywords } from '@/mocks/profile';
import { careerOptions, jobOptions } from '@/mocks/auth';
import { interestOptions, stackOptions } from '@/mocks/home';
import type { AuthUser, AccountRole } from '@/types/auth';
import type { CollaborationReview, PortfolioInput, ProfileAction, ProfileState, Collaboration } from '@/types/profile';
export const PROFILE_KEY='devmozip:profiles:v1';
export function freshProfileState():ProfileState{return structuredClone(initialProfileState);}
export function allProfiles(state:ProfileState,users:AuthUser[]){const profiles={...initialProfileState.profiles,...state.profiles};for(const u of users)if(!profiles[u.id])profiles[u.id]=profileFromAccount(u);return profiles;}
export function canViewPortfolioDetail(ownerId:string,viewerId:string|null,role?:AccountRole){return ownerId===viewerId||role==='premium'||role==='admin';}
export function portfolioFeedback(input:PortfolioInput){return [input.features.trim()?'주요 기능이 기록되어 있어 역할을 이해하기 좋아요. 사용자 흐름과 기여 범위를 추가해 보세요.':'주요 구현 기능과 본인의 기여 범위를 추가해 보세요.',input.challenge.trim()?'문제 해결 경험을 원인 → 선택 → 결과 순으로 구체화해 보세요.':'가장 어려웠던 문제와 해결 방법을 기록해 보세요.',input.outcome.trim()?'결과에 수치나 사용자 피드백을 더하면 전달력이 높아져요.':'개발 결과와 배운 점을 추가하면 더 좋아요.'];}
export function canOpenPortfolioDetail(role?:AccountRole,ownerId?:string,viewerId?:string|null){return !!viewerId&&!!ownerId&&ownerId===viewerId||role==='premium'||role==='admin';}
export function canUsePortfolioFeedback(ownerId:string,viewerId:string|null,role?:AccountRole){return ownerId===viewerId&&canOpenPortfolioDetail(role);}
export function portfolioErrors(input:PortfolioInput){const e:string[]=[...portfolioMediaErrors(input),...portfolioBlockErrors(input)];if(input.coverImage!==undefined&&(typeof input.coverImage!=='string'||!validAvatar(input.coverImage)))e.push('대표 이미지는 PNG/JPEG/WebP, 256KB 이하로 선택해 주세요.');if(input.field!==undefined&&(typeof input.field!=='string'||input.field.length>40))e.push('분야는 40자 이내로 입력해 주세요.');if(!input.title.trim()||input.title.length>100)e.push('프로젝트명은 1~100자로 입력해 주세요.');if(!input.summary.trim()||input.summary.length>200)e.push('한 줄 소개는 1~200자로 입력해 주세요.');if(!/^\d{4}-(0[1-9]|1[0-2])$/.test(input.start)||!/^\d{4}-(0[1-9]|1[0-2])$/.test(input.end)||input.end<input.start)e.push('시작과 종료 월을 올바르게 입력해 주세요.');if(!input.role.trim()||input.role.length>100)e.push('담당 역할은 1~100자로 입력해 주세요.');if(!input.stacks.length||input.stacks.length>30||input.stacks.some(s=>!s.trim()||s.length>30)||new Set(input.stacks.map(s=>s.toLowerCase())).size!==input.stacks.length)e.push('기술 스택을 하나 이상 선택해 주세요.');if([input.description,input.features,input.challenge,input.outcome].some(s=>s.length>3000))e.push('상세 항목은 각각 3,000자 이내로 입력해 주세요.');return e;}
export function temperature(reviews:CollaborationReview[],targetId:string){return Math.max(0,Math.min(100,Math.round((reviewPolicy.temperatureBase+reviews.filter(r=>r.targetId===targetId).reduce((sum,r)=>sum+(r.rating-reviewPolicy.ratingCenter)*reviewPolicy.temperatureFactor,0))*10)/10));}
export function starColor(temp:number){return temp<36.5?'#9397a5':temp<38?'#00afc1':temp<40?'#7955df':'#d99c30';}
export function keywordStats(reviews:CollaborationReview[],targetId:string,keywords=reviewKeywords){const relevant=reviews.filter(r=>r.targetId===targetId);const total=relevant.reduce((sum,r)=>sum+r.keywords.length,0);return [...new Set([...keywords,...relevant.flatMap(r=>r.keywords)])].map(keyword=>{const count=relevant.filter(r=>r.keywords.includes(keyword)).length;return {keyword,count,percent:total?Math.round(count/total*100):0};});}
export function reviewEligibility(state:ProfileState,authorId:string|null,targetId:string,projectId:string,now=new Date(),history:Collaboration[]=collaborations):string{
 if(!authorId)return '로그인한 실제 협업자만 리뷰를 작성할 수 있습니다.';
 if(authorId===targetId)return '본인에게는 리뷰를 작성할 수 없습니다.';
 const project=history.find(c=>c.projectId===projectId);
 if(!project||!project.members.some(m=>m.userId===authorId)||!project.members.some(m=>m.userId===targetId))return '이 프로젝트에서 함께 협업한 기록이 있어야 합니다.';
 if(project.status!=='프로젝트 종료')return '프로젝트가 종료된 후 리뷰를 작성할 수 있습니다.';
 const end=project.endedAt?Date.parse(project.endedAt):new Date(`${project.end}T23:59:59+09:00`).getTime();const diff=now.getTime()-end;
 if(diff<0||diff>reviewPolicy.windowDays*24*60*60*1000)return '데모 리뷰 작성 기간(프로젝트 종료 후 30일)이 아닙니다.';
 if(state.reviews.some(r=>r.projectId===projectId&&r.authorId===authorId&&r.targetId===targetId))return '이 프로젝트의 협업 리뷰를 이미 작성했습니다.';
 return '';
}
export function applyProfileAction(state:ProfileState,actorId:string|null,action:ProfileAction,now=new Date(),history:Collaboration[]=collaborations,allowedKeywords=reviewKeywords):ProfileState{
 if(action.type.startsWith('social-'))return applySocialAction(state,actorId,action as SocialAction,now);
 if(!actorId||!Object.hasOwn(state.profiles,actorId))throw new Error('로그인 후 이용해 주세요.');
 const next=structuredClone(state);
 const ownPortfolio=(id:string)=>{const p=next.portfolios.find(p=>p.id===id);if(!p||p.ownerId!==actorId)throw new Error('본인 포트폴리오만 관리할 수 있습니다.');return p;};
 const ownPost=(id:string)=>{const p=next.posts.find(p=>p.id===id);if(!p||p.authorId!==actorId)throw new Error('본인 게시글만 관리할 수 있습니다.');return p;};
 const ownRepost=(id:string)=>{const p=next.reposts.find(p=>p.id===id);if(!p||p.ownerId!==actorId)throw new Error('본인이 퍼간 글만 관리할 수 있습니다.');return p;};
 switch(action.type){
 case 'reviews-batch':{if(!action.entries.length||new Set(action.entries.map(e=>e.targetId)).size!==action.entries.length)throw new Error('평가할 팀원을 확인해 주세요.');for(const entry of action.entries){const reason=reviewEligibility(next,actorId,entry.targetId,action.projectId,now,history);if(reason)throw new Error(reason);if(!Number.isInteger(entry.rating)||entry.rating<1||entry.rating>5)throw new Error('모든 팀원의 별점을 선택해 주세요.');if(entry.keywords.some(k=>!allowedKeywords.includes(k))||new Set(entry.keywords).size!==entry.keywords.length)throw new Error('리뷰 키워드를 확인해 주세요.');next.reviews.unshift({id:createMockId(),projectId:action.projectId,authorId:actorId,targetId:entry.targetId,rating:entry.rating,keywords:[...entry.keywords],createdAt:now.toISOString()});}break;}

 case 'onboarding':{const input=sanitizeDraft(action.input);if(!input)throw new Error('온보딩 입력을 확인해 주세요.');const duplicate=Object.values(next.profiles).some(p=>p.id!==actorId&&p.nickname.toLowerCase()===input.nickname.trim().toLowerCase());const errors=onboardingErrors(input,3,duplicate);if(errors.length)throw new Error(errors[0]);const p=next.profiles[actorId];Object.assign(p,{nickname:input.nickname.trim(),bio:input.bio.trim(),avatar:input.avatar,jobs:input.jobs,job:input.jobs[0],career:input.career,stacks:input.stacks,interests:[...input.interests,...input.customFields],customFields:input.customFields,available:input.available,projectInterests:input.projectInterests,contact:input.contact.trim(),github:input.github.trim(),blog:input.blog.trim()});break;}

 case 'follow-publish':publishFollowEvent(next,actorId,action.kind,action.recordId,action.title,now);break;
 case 'follow-preferences':if(!next.following[actorId]?.includes(action.targetId))throw new Error('팔로우한 개발자의 알림만 설정할 수 있습니다.');if(typeof action.preferences.activity!=='boolean'||typeof action.preferences.projects!=='boolean')throw new Error('알림 설정을 확인해 주세요.');next.followPreferences[actorId]={...(next.followPreferences[actorId]||{}),[action.targetId]:{...action.preferences}};break;
 case 'profile':{const p={...action.patch};if(['avatar','banner'].some(key=>{const value=p[key as 'avatar'|'banner'];return value!==undefined&&(typeof value!=='string'||!validAvatar(value));}))throw new Error('프로필/배너 이미지 형식과 크기를 확인해 주세요.');if(p.nickname!==undefined){p.nickname=p.nickname.trim();if(!validNickname(p.nickname))throw new Error('닉네임은 한글·영문·숫자·공백·밑줄로 2~20자를 입력해 주세요.');if(Object.values(next.profiles).some(profile=>profile.id!==actorId&&profile.nickname.toLowerCase()===p.nickname!.toLowerCase()))throw new Error('이미 사용 중인 닉네임입니다.');}if(p.handle!==undefined){p.handle=p.handle.trim();if(!/^[a-zA-Z0-9_-]{2,30}$/.test(p.handle))throw new Error('태그는 @ 없이 영문·숫자·밑줄·하이픈으로 2~30자를 입력해 주세요.');if(Object.values(next.profiles).some(profile=>profile.id!==actorId&&profile.handle.toLowerCase()===p.handle!.toLowerCase()))throw new Error('이미 사용 중인 태그입니다.');}if(p.introductionFormat!==undefined&&!['text','html'].includes(p.introductionFormat))throw new Error('소개 작성 형식을 확인해 주세요.');if(p.bio.length>150||p.introduction.length>introductionPolicy.maxLength||p.contact.length>100)throw new Error('소개 또는 연락 시간의 최대 길이를 확인해 주세요.');if((p.github&&!safeExternalUrl(p.github))||(p.blog&&!safeExternalUrl(p.blog)))throw new Error('외부 링크는 http 또는 https 주소를 입력해 주세요.');if(!jobOptions.includes(p.job)||!careerOptions.includes(p.career)||!p.stacks.length||p.stacks.length>30||p.stacks.some(s=>!s.trim()||s.length>30)||new Set(p.stacks.map(s=>s.trim().toLowerCase())).size!==p.stacks.length||!p.interests.length||p.interests.length>30||p.interests.some(s=>!s.trim()||s.length>30)||new Set(p.interests.map(s=>s.trim().toLowerCase())).size!==p.interests.length)throw new Error('직군·경력·기술·관심 분야를 올바르게 선택해 주세요.');if(p.externalLinks!==undefined&&(!Array.isArray(p.externalLinks)||p.externalLinks.length>10||p.externalLinks.some(link=>!link||typeof link.label!=='string'||!link.label.trim()||link.label.length>40||typeof link.url!=='string'||link.url.length>500||!safeExternalUrl(link.url))))throw new Error('추가 링크는 이름(40자 이내)과 http/https URL(500자 이내)을 입력해 주세요.');next.profiles[actorId]={...next.profiles[actorId],...p,...(p.externalLinks!==undefined?{externalLinks:p.externalLinks.map(link=>({label:link.label.trim(),url:safeExternalUrl(link.url)}))}:{}),stacks:p.stacks.map(s=>s.trim()),interests:p.interests.map(s=>s.trim()),introduction:p.introductionFormat==='html'?sanitizeIntroduction(p.introduction):p.introduction,jobs:[p.job,...(next.profiles[actorId].jobs||[]).filter(job=>job!==p.job)]};break;}
 case 'portfolio-save':{const originalErrors=portfolioErrors(action.input);if(originalErrors.length)throw new Error(originalErrors[0]);const input=preparePortfolioBlocks(action.input);const errors=portfolioErrors(input);if(errors.length)throw new Error(errors[0]);if(action.id){ownPortfolio(action.id);next.portfolios=next.portfolios.map(p=>p.id===action.id?{...p,...input}:p);}else next.portfolios.unshift({...input,id:createMockId(),ownerId:actorId});break;}
 case 'portfolio-delete':ownPortfolio(action.id);next.portfolios=next.portfolios.filter(p=>p.id!==action.id);break;
 case 'post-edit':{const p=ownPost(action.id);if(!action.title.trim()||action.title.length>150||!action.body.trim()||action.body.length>5000)throw new Error('제목(150자)과 본문(5,000자)을 올바르게 입력해 주세요.');p.title=action.title.trim();p.body=action.body;break;}
 case 'post-hide':{const p=ownPost(action.id);p.hidden=!p.hidden;break;}
 case 'post-delete':ownPost(action.id);cleanupPostSocial(next,action.id);next.posts=next.posts.filter(p=>p.id!==action.id);break;
 case 'repost-add':if((action.opinion||'').length>1000)throw new Error('퍼가기 의견은 1,000자 이내로 입력해 주세요.');if(!next.posts.some(p=>p.id===action.originalId&&!p.hidden))throw new Error('원본 글을 찾을 수 없습니다.');if(next.reposts.some(p=>p.originalId===action.originalId&&p.ownerId===actorId))throw new Error('이미 내 프로필에 퍼간 글입니다.');next.reposts.unshift({id:createMockId(),ownerId:actorId,originalId:action.originalId,opinion:action.opinion||'',hidden:false});{const original=next.posts.find(p=>p.id===action.originalId)!;addActivityNotice(next,actorId,original.authorId,'repost',original.id,'게시글 퍼가기',`${next.profiles[actorId].nickname}님이 ${original.title} 글을 퍼갔습니다.`,`/posts/${original.id}`,now);}break;
 case 'repost-edit':{if(action.opinion.length>1000)throw new Error('퍼가기 의견은 1,000자 이내로 입력해 주세요.');ownRepost(action.id).opinion=action.opinion;break;}
 case 'repost-hide':{const p=ownRepost(action.id);p.hidden=!p.hidden;break;}
 case 'repost-delete':ownRepost(action.id);next.reposts=next.reposts.filter(p=>p.id!==action.id);break;
 case 'follow':{if(actorId===action.targetId||!Object.hasOwn(next.profiles,action.targetId))throw new Error('팔로우할 사용자를 확인해 주세요.');const current=next.following[actorId]||[];if(!current.includes(action.targetId))addActivityNotice(next,actorId,action.targetId,'follow',action.targetId,'새 팔로워',`${next.profiles[actorId].nickname}님이 팔로우했습니다.`,`/users/${actorId}`,now);if(current.includes(action.targetId)&&next.followPreferences[actorId])delete next.followPreferences[actorId][action.targetId];next.following[actorId]=current.includes(action.targetId)?current.filter(id=>id!==action.targetId):[...current,action.targetId];break;}
 case 'review':{const reason=reviewEligibility(next,actorId,action.targetId,action.projectId,now,history);if(reason)throw new Error(reason);if(!Number.isInteger(action.rating)||action.rating<1||action.rating>5)throw new Error('별점을 1~5점 중 선택해 주세요.');if(action.keywords.some(k=>!allowedKeywords.includes(k))||new Set(action.keywords).size!==action.keywords.length)throw new Error('리뷰 키워드를 확인해 주세요.');next.reviews.unshift({id:createMockId(),projectId:action.projectId,authorId:actorId,targetId:action.targetId,rating:action.rating,keywords:action.keywords,createdAt:now.toISOString().slice(0,10)});break;}
 }
 return next;
}
// A strict structural check prevents malformed storage from breaking every profile page.
export function parseProfileState(raw:string|null):ProfileState{
 try{const data=JSON.parse(raw||'null');if(data?.version!==1||!data.profiles||typeof data.profiles!=='object'||Array.isArray(data.profiles)||!Array.isArray(data.portfolios)||!Array.isArray(data.posts)||!Array.isArray(data.reposts)||!Array.isArray(data.reviews)||!data.following||typeof data.following!=='object'||Array.isArray(data.following))return freshProfileState();
 const strings=(v:unknown)=>Array.isArray(v)&&v.every(s=>typeof s==='string');
 if(!Object.entries(data.profiles).every(([key,p]:[string,any])=>p&&p.id===key&&(p.introductionFormat===undefined||['text','html'].includes(p.introductionFormat))&&['id','nickname','handle','job','career','bio','introduction','joinedAt','github','blog','contact','color'].every(k=>typeof p[k]==='string')&&typeof p.available==='boolean'&&strings(p.stacks)&&strings(p.interests)))return freshProfileState();
 if(Object.values(data.profiles).some((p:any)=>['avatar','banner'].some(key=>p[key]!==undefined&&(typeof p[key]!=='string'||!validAvatar(p[key])))||['jobs','customFields','projectInterests'].some(k=>p[k]!==undefined&&!strings(p[k]))))return freshProfileState();
 if(Object.values(data.profiles).some((p:any)=>p.externalLinks!==undefined&&(!Array.isArray(p.externalLinks)||p.externalLinks.length>10||p.externalLinks.some((link:any)=>!link||typeof link.label!=='string'||!link.label.trim()||link.label.length>40||typeof link.url!=='string'||link.url.length>500||!safeExternalUrl(link.url)))))return freshProfileState();
 if(!data.portfolios.every((p:any)=>p&&['id','ownerId','title','summary','start','end','role','description','features','challenge','outcome'].every(k=>typeof p[k]==='string')&&strings(p.stacks)))return freshProfileState();
 if(data.portfolios.some((p:any)=>(p.coverImage!==undefined&&(typeof p.coverImage!=='string'||!validAvatar(p.coverImage)))||(p.field!==undefined&&(typeof p.field!=='string'||p.field.length>40))))return freshProfileState();
 if(data.portfolios.some((p:any)=>p.blocks!==undefined&&!validPortfolioBlocks(p.blocks)))return freshProfileState();
 if(data.portfolios.some((p:any)=>portfolioMediaErrors(p).length))return freshProfileState();
 if(!data.posts.every((p:any)=>p&&['id','authorId','title','body','category','color'].every(k=>typeof p[k]==='string')&&strings(p.tags)&&typeof p.likes==='number'&&typeof p.comments==='number'&&typeof p.hidden==='boolean'))return freshProfileState();
 if(!data.reposts.every((p:any)=>p&&['id','ownerId','originalId','opinion'].every(k=>typeof p[k]==='string')&&typeof p.hidden==='boolean'))return freshProfileState();
 if(!data.reviews.every((p:any)=>p&&['id','projectId','authorId','targetId','createdAt'].every(k=>typeof p[k]==='string')&&Number.isInteger(p.rating)&&p.rating>=1&&p.rating<=5&&strings(p.keywords)&&p.keywords.every((k:string)=>k.trim().length>0&&k.length<=30)&&new Set(p.keywords).size===p.keywords.length))return freshProfileState();

 if(!data.posts.every((p:any)=>Object.hasOwn(data.profiles,p.authorId))||!data.portfolios.every((p:any)=>Object.hasOwn(data.profiles,p.ownerId))||!data.reposts.every((p:any)=>Object.hasOwn(data.profiles,p.ownerId)))return freshProfileState();
 if(!Object.values(data.following).every(v=>strings(v)))return freshProfileState();
 if(data.social!==undefined&&!parseSocial(data.social))return freshProfileState();
 if(data.posts.some((p:any)=>['github','external','createdAt','updatedAt'].some(k=>p[k]!==undefined&&typeof p[k]!=='string')||(p.views!==undefined&&(!Number.isFinite(p.views)||p.views<0))))return freshProfileState();
 if(data.posts.some((p:any)=>p.interests!==undefined&&!validPostTags(p.interests)))return freshProfileState();
 if(data.posts.some((p:any)=>p.images!==undefined&&(!Array.isArray(p.images)||p.images.length>3||!p.images.every(validImage))))return freshProfileState();
 if(data.followPreferences!==undefined&&(!data.followPreferences||typeof data.followPreferences!=='object'||Array.isArray(data.followPreferences)||!Object.values(data.followPreferences).every((v:any)=>v&&typeof v==='object'&&!Array.isArray(v)&&Object.values(v).every((p:any)=>p&&typeof p.activity==='boolean'&&typeof p.projects==='boolean'))))return freshProfileState();
 if(data.followNotices!==undefined&&(!Array.isArray(data.followNotices)||!data.followNotices.every((n:any)=>n&&['id','recipientId','actorId','recordId','title','createdAt'].every(k=>typeof n[k]==='string')&&['activity','project'].includes(n.kind))))return freshProfileState();
 if(data.socialNotices!==undefined&&(!Array.isArray(data.socialNotices)||!data.socialNotices.every((n:any)=>n&&['id','recipientId','actorId','title','body','href','createdAt','eventKey'].every(k=>typeof n[k]==='string')&&['follow','like','comment','repost'].includes(n.kind))))return freshProfileState();
 return {...data,portfolios:data.portfolios.map(upgradePortfolioMock),socialNotices:data.socialNotices||[],followPreferences:data.followPreferences||{},followNotices:data.followNotices||[],social:data.social||legacySocial(data.posts)} as ProfileState;}catch{return freshProfileState();}
}
export function loadProfiles(){try{return parseProfileState(localStorage.getItem(PROFILE_KEY));}catch{return freshProfileState();}}
export function saveProfiles(state:ProfileState){try{localStorage.setItem(PROFILE_KEY,JSON.stringify(state));return true;}catch{return false;}}
