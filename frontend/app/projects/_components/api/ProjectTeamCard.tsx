import Link from 'next/link';
import { Crown, ChevronRight } from 'lucide-react';
import { Card } from '@/components/common/Card';
import { projectRoleLabel } from '@/lib/projects/projectChoices';
import type { ProjectDetailResult } from '@/types/projectApi';
import styles from './ProjectTeamCard.module.css';

function Member({name,tag,role,host=false}:{name:string;tag:string|null;role:string;host?:boolean}) {
 const canVisit=tag&&/^[A-Za-z0-9_-]{1,100}$/.test(tag)&&tag.toLowerCase()!=='me';
 const content=<><span className={styles.avatar} aria-hidden="true">{name.charAt(0)}</span><span className={styles.identity}><strong>{name}{host&&<Crown className={styles.crown} size={18} aria-label="주최자"/>}</strong><span>{role}</span></span>{canVisit&&<ChevronRight size={18} className={styles.arrow} aria-hidden="true"/>}</>;
 return canVisit?<Link className={`${styles.member} ${host?styles.host:''}`} href={'/users/'+encodeURIComponent(tag)} aria-label={`${name} 프로필 보기`}>{content}</Link>:<div className={`${styles.member} ${host?styles.host:''}`}>{content}</div>;
}
export function ProjectTeamCard({host,hostMemberId,team}:{host:ProjectDetailResult['host'];hostMemberId:number;team:ProjectDetailResult['team']}){
 const participants=team?.filter(member=>member.userId!==hostMemberId&&member.role!=='HOST');
 return <Card className={styles.card}>
  <h3>프로젝트 팀원</h3>
  <Member name={host?.nickname||'주최자'} tag={host?.tag??null} role="프로젝트 주최자" host/>
  <div className={styles.heading}><h4>참여 팀원</h4>{participants&&<span>{participants.length}명</span>}</div>
  {participants?.length?<ul className={styles.list}>{participants.map(member=><li key={member.userId}><Member name={member.nickname||'팀원'} tag={member.tag} role={member.jobRole?projectRoleLabel(member.jobRole):'프로젝트 참여자'}/></li>)}</ul>:<p className={styles.empty}>{participants?'아직 참여 팀원이 없습니다.':'팀원 정보를 아직 불러오지 못했습니다.'}</p>}
 </Card>;
}
