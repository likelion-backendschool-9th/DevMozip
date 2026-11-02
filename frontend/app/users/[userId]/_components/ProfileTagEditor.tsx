'use client';
import {useState} from 'react';
import {Input} from '@/components/common/Input';
import {Plus,X} from 'lucide-react';
import styles from './ProfileTagEditor.module.css';
export function ProfileTagEditor({label,options,value,onChange}:{label:string;options:readonly string[];value:string[];onChange:(value:string[])=>void}){
 const [text,setText]=useState('');const [error,setError]=useState('');
 const add=()=>{const item=text.trim();if(!item||item.length>30){setError('1~30자로 입력해 주세요.');return;}if(value.some(v=>v.toLowerCase()===item.toLowerCase())){setError('이미 추가한 항목입니다.');return;}if(value.length>=30){setError('최대 30개까지 추가할 수 있습니다.');return;}onChange([...value,item]);setText('');setError('');};
 return <fieldset className={styles.fieldset}><legend>{label} · 하나 이상 선택</legend><div className="chips">{options.map(item=><button type="button" key={item} className={`chip ${value.includes(item)?'selected':''}`} aria-pressed={value.includes(item)} onClick={()=>onChange(value.includes(item)?value.filter(v=>v!==item):[...value,item])}>{item}</button>)}</div><label>{label} 직접 입력<div className={styles.inputRow}><Input value={text} maxLength={30} onChange={e=>{setText(e.target.value);setError('');}} onKeyDown={e=>{if(e.key==='Enter'&&!e.nativeEvent.isComposing){e.preventDefault();add();}}}/><button type="button" className={styles.addButton} aria-label={`${label} 추가`} title={`${label} 추가`} onClick={add}><Plus size={18} aria-hidden="true"/></button></div></label>{error&&<p className="form-error" role="alert">{error}</p>}<div className="chips" aria-label={`선택한 ${label}`}>{value.map(item=><button type="button" key={item} className="chip selected" aria-label={`${label} ${item} 삭제`} onClick={()=>onChange(value.filter(v=>v!==item))}>{item}<X size={13} aria-hidden="true"/></button>)}</div></fieldset>;
}
