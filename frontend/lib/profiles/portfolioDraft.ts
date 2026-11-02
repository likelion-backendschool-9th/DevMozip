import {validAvatar} from '@/lib/account/onboardingService';
import {validPortfolioBlocks} from './portfolioBlocks';
import {portfolioMediaErrors} from './portfolioMedia';
import type {PortfolioInput} from '@/types/profile';
export function portfolioDraftKey(userId:string,portfolioId?:string){return `devmozip:portfolio-draft:v1:${userId}:${portfolioId||'new'}`;}
export function loadPortfolioDraft(key:string):PortfolioInput|null{try{const data=JSON.parse(localStorage.getItem(key)||'null');const p=data?.input;if(data?.version!==1||!p||!['title','summary','start','end','role','description','features','challenge','outcome'].every(k=>typeof p[k]==='string')||!['field','coverImage'].every(k=>p[k]===undefined||typeof p[k]==='string')||typeof p.title!=='string'||typeof p.summary!=='string'||p.coverImage!==undefined&&!validAvatar(p.coverImage)||!Array.isArray(p.stacks)||!p.stacks.every((v:unknown)=>typeof v==='string')||!validPortfolioBlocks(p.blocks,true)||portfolioMediaErrors({...p,links:undefined}).length||p.links!==undefined&&(!Array.isArray(p.links)||p.links.length>6||p.links.some((l:any)=>!l||typeof l.label!=='string'||l.label.length>40||typeof l.url!=='string'||l.url.length>500)))return null;return p;}catch{return null;}}
export function savePortfolioDraft(key:string,input:PortfolioInput){try{localStorage.setItem(key,JSON.stringify({version:1,input}));return true;}catch{return false;}}
export function clearPortfolioDraft(key:string){try{localStorage.removeItem(key);}catch{/* Published state is independent of drafts. */}}
