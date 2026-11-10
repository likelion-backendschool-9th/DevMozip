import Link from 'next/link';
import {Siren} from 'lucide-react';
import styles from './ProjectIconActions.module.css';
export function ProjectReportButton({projectId,reported=false}:{projectId:string|number;reported?:boolean}){
 const label=reported?'신고 접수 안내':'프로젝트 신고';
 return <Link href={'/projects/'+projectId+'/report'} className={[styles.icon,styles.report].join(' ')} title={label} aria-label={label}><Siren size={21}/></Link>;
}
