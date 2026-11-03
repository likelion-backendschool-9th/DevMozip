import sanitizeHtml from 'sanitize-html';
import {introductionPolicy} from '@/mocks/profileIntroduction';
import {safeExternalUrl} from '@/lib/utils/externalUrl';
const color=[/^#[0-9a-f]{3,8}$/i,/^[a-z]{1,24}$/i,/^rgba?\(\s*[\d.,%\s]+\)$/i];
const spacing=[/^(?:0|\d{1,2}px|[0-3](?:\.\d{1,2})?rem)(?:\s+(?:0|\d{1,2}px|[0-3](?:\.\d{1,2})?rem)){0,3}$/];
// Shared by save and render: old/tampered storage is also filtered before display.
// Real persistence will need the same validation on the future backend.
export function sanitizeIntroduction(source:string){
 const markup=/<\/?[a-z][^>]*>/i.test(source)?source:source.replace(/\r?\n/g,'<br />');
 return sanitizeHtml(markup,{
  allowedTags:['p','br','div','span','h1','h2','h3','h4','h5','h6','strong','b','em','i','u','s','small','mark','sub','sup','blockquote','ul','ol','li','pre','code','hr','a','table','thead','tbody','tfoot','tr','th','td','caption','figure','figcaption','img'],
  allowedAttributes:{'*':['style'],a:['href','title','target','rel'],img:['src','alt','title','loading','referrerpolicy'],th:['colspan','rowspan'],td:['colspan','rowspan']},
  allowedSchemes:['http','https'],allowProtocolRelative:false,
  allowedStyles:{'*':{
   color,'background-color':color,'text-align':[/^(left|right|center|justify)$/],
   'font-size':[/^(?:[89]|[1-6]\d|7[0-2])px$/, /^(?:0\.[89]|[1-3](?:\.\d{1,2})?)rem$/],
   'font-weight':[/^(?:normal|bold|[1-9]00)$/],'font-style':[/^(normal|italic)$/],
   'text-decoration':[/^(none|underline|line-through)$/],'line-height':[/^(?:[1-2](?:\.\d{1,2})?|3)$/],
   margin:spacing,padding:spacing,'border-radius':spacing,
   'margin-top':spacing,'margin-bottom':spacing,'padding-top':spacing,'padding-bottom':spacing,
   width:[/^(?:100|[1-9]?\d)%$/],'max-width':[/^(?:100|[1-9]?\d)%$/],
   display:[/^(block|inline|inline-block|flex|grid)$/],gap:spacing,
   'flex-wrap':[/^(wrap|nowrap)$/],'flex-direction':[/^(row|column)$/],
   'align-items':[/^(start|end|center|stretch|flex-start|flex-end)$/],
   'justify-content':[/^(start|end|center|space-between|space-around|flex-start|flex-end)$/]
  }},
  transformTags:{
   a:(_tag,attributes)=>{const href=attributes.href?safeExternalUrl(attributes.href):'';return {tagName:'a',attribs:{...attributes,href,target:'_blank',rel:'noopener noreferrer'}};},
   img:(_tag,attributes)=>({tagName:'img',attribs:{...attributes,src:attributes.src?safeExternalUrl(attributes.src):'',loading:'lazy',referrerpolicy:'no-referrer',alt:attributes.alt||''}})
  },
  exclusiveFilter:frame=>frame.tag==='img'&&!frame.attribs.src
 });
}
export function introductionHtml(source:string){return sanitizeIntroduction(source.slice(0,introductionPolicy.maxLength));}
