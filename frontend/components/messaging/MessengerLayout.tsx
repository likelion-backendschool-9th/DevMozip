import type {ReactNode} from 'react';
import styles from './MessengerLayout.module.css';
export {default as messengerStyles} from './MessengerLayout.module.css';
export function MessengerLayout({children}:{children:ReactNode}){return <div className={`${styles.scope} messenger-layout`}>{children}</div>;}
