import {roleLabels} from './projectApiContract';
export const normalizeProjectChoice=(value:string)=>value.normalize('NFKC').trim().replace(/ +/g,' ');
export const projectChoiceKey=(value:string)=>normalizeProjectChoice(value).toLowerCase();
export const validProjectChoice=(value:string)=>!!normalizeProjectChoice(value)&&normalizeProjectChoice(value).length<=30&&!/\p{Cc}/u.test(value);
export function projectRoleCode(value:string){
 const text=normalizeProjectChoice(value);
 if(text==='디자이너')return 'DESIGNER';
 return Object.entries(roleLabels).find(([code,label])=>projectChoiceKey(code)===projectChoiceKey(text)||projectChoiceKey(label)===projectChoiceKey(text))?.[0]??text;
}
export function projectRoleLabel(value:string){const code=projectRoleCode(value);return Object.hasOwn(roleLabels,code)?roleLabels[code]:code;}
