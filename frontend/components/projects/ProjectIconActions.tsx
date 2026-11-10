import type {ReactNode} from 'react';
import styles from './ProjectIconActions.module.css';
export function ProjectIconActions({children}:{children:ReactNode}){return <div className={styles.group} role="group" aria-label="프로젝트 반응 및 공유">{children}</div>;}
