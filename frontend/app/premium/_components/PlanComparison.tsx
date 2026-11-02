'use client';
import styles from './PlanComparison.module.css';
import { planFeatures } from '@/mocks/administration';
export function PlanComparison() { return <div className={[styles.scope, "management-table-scroll"].join(" ")}><table className={[styles.scope, "management-table"].join(" ")}><caption>무료 / 프리미엄 기능 비교</caption><thead><tr><th>기능</th><th>무료</th><th>프리미엄 <span className={[styles.scope, "premium-label"].join(" ")}>PRO</span></th></tr></thead><tbody>{planFeatures.map(([label, free, premium]) => <tr key={label}><th scope="row">{label}</th><td>{free}</td><td>{premium}</td></tr>)}</tbody></table></div>; }
