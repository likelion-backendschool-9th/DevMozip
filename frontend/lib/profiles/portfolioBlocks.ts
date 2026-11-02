import {portfolioMediaErrors} from './portfolioMedia';
import {marked} from 'marked';
import {sanitizeIntroduction} from './profileIntroduction';
import {portfolioBlockTemplates,portfolioEditorPolicy} from '@/mocks/portfolioEditor';
import type {PortfolioBlock,PortfolioInput} from '@/types/profile';
export function getPortfolioBlocks(input:PortfolioInput):PortfolioBlock[]{
 const original=input.blocks!==undefined?input.blocks.map(block=>({...block})):portfolioBlockTemplates.map(template=>({...template,content:template.source?input[template.source]||'':''}));
 if(input.blocks===undefined&&input.links?.length)original.push({id:'project-links',kind:'links',title:'프로젝트 링크',content:'',format:'text'});
 const blocks:PortfolioBlock[]=[];const ids=new Set(original.map(b=>b.id));
 const idFor=(id:string,suffix:string)=>{let value=`${id.slice(0,60)}-${suffix}`;let i=1;while(ids.has(value))value=`${id.slice(0,55)}-${suffix}-${i++}`;ids.add(value);return value;};
 for(const block of original){
  const {images,links,...content}=block;
  if(block.kind==='text'){blocks.push(content);if(images?.length)blocks.push({id:idFor(block.id,'images'),kind:'gallery',title:`${block.title} 이미지`.slice(0,80),content:'',format:'text',images});if(links?.length)blocks.push({id:idFor(block.id,'links'),kind:'links',title:`${block.title} 링크`.slice(0,80),content:'',format:'text',links});}
  else if(block.kind==='gallery'){blocks.push({...content,...(images!==undefined?{images}:{})});if(links?.length)blocks.push({id:idFor(block.id,'links'),kind:'links',title:'프로젝트 링크',content:'',format:'text',links});}
  else{blocks.push({...content,...(links!==undefined?{links}:{})});if(images?.length)blocks.push({id:idFor(block.id,'images'),kind:'gallery',title:'프로젝트 이미지',content:'',format:'text',images});}
 }
 return blocks;
}
export function portfolioFields(input:Pick<PortfolioInput,'field'|'fields'>){return input.fields!==undefined?input.fields:input.field?[input.field]:[];}
export function validPortfolioBlocks(value:unknown,allowIncomplete=false):value is PortfolioBlock[]{return Array.isArray(value)&&value.length>=1&&value.length<=portfolioEditorPolicy.maxBlocks&&value.every(b=>b&&typeof b.id==='string'&&/^[a-zA-Z0-9_-]{1,80}$/.test(b.id)&&typeof b.title==='string'&&b.title.length<=80&&typeof b.content==='string'&&b.content.length<=20000&&['text','gallery','links'].includes(b.kind)&&['text','markdown','html'].includes(b.format)&&portfolioMediaErrors({...b,links:allowIncomplete?undefined:b.links}).length===0&&(b.links===undefined||Array.isArray(b.links)&&b.links.length<=6&&b.links.every((link:any)=>link&&typeof link.label==='string'&&link.label.length<=40&&typeof link.url==='string'&&link.url.length<=500))&&(b.source===undefined||['description','contribution','features','challenge','outcome'].includes(b.source)))&&new Set(value.map(b=>b.id)).size===value.length&&new Set(value.filter(b=>b.source).map(b=>b.source)).size===value.filter(b=>b.source).length;}
export function portfolioBlockErrors(input:PortfolioInput){if(input.blocks===undefined)return [];if(!validPortfolioBlocks(input.blocks,true))return ['섹션은 1~20개, 제목 80자·본문 20,000자 이내로 올바르게 구성해 주세요.'];for(const block of input.blocks){const errors=portfolioMediaErrors(block);if(errors.length)return [`${block.title}: ${errors[0]}`];}if(input.blocks.some(b=>!b.title.trim()))return ['모든 섹션의 제목을 입력해 주세요.'];return [];}
export function portfolioBlockHtml(block:Pick<PortfolioBlock,'content'|'format'>){const source=block.content.slice(0,20000);return sanitizeIntroduction(block.format==='markdown'?marked.parse(source,{async:false,gfm:true}):source);}
export function preparePortfolioBlocks(input:PortfolioInput):PortfolioInput{
 const fields=input.fields?.map(value=>value.trim());input=fields?{...input,fields,field:fields[0]||''}:input;
 if(input.blocks===undefined)return input;
 const next={...input,blocks:getPortfolioBlocks(input).map(block=>({...block,title:block.title.trim(),content:block.format==='html'?sanitizeIntroduction(block.content):block.content}))};
 const gallery=next.blocks.find(block=>block.kind==='gallery');const links=next.blocks.find(block=>block.kind==='links');next.images=gallery?portfolioBlockImages(input,gallery):[];next.links=links?portfolioBlockLinks(input,links):[];
 for(const source of ['description','contribution','features','challenge','outcome'] as const){const block=next.blocks.find(b=>b.source===source);next[source]=block?block.content.replace(/<[^>]*>/g,'').slice(0,3000):'';}
 return next;
}
export function movePortfolioBlock(blocks:PortfolioBlock[],fromId:string,toId:string){const from=blocks.findIndex(b=>b.id===fromId);const to=blocks.findIndex(b=>b.id===toId);if(from<0||to<0||from===to)return blocks;const next=[...blocks];const [block]=next.splice(from,1);next.splice(to,0,block);return next;}

export function portfolioBlockImages(input:PortfolioInput,block:PortfolioBlock){return block.images??(block.kind==='gallery'?input.images||[]:[]);}
export function portfolioBlockLinks(input:PortfolioInput,block:PortfolioBlock){return block.links??(block.kind==='links'?input.links||[]:[]);}
