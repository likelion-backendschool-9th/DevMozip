'use client';
import styles from './TagChoices.module.css';
export function TagChoices({ label, options, values, onChange }: {
    label: string;
    options: readonly string[];
    values: string[];
    onChange: (values: string[]) => void;
}) { return <fieldset className={[styles.scope, "onboarding-tags"].join(" ")}><legend>{label}</legend><div className={[styles.scope, "chips"].join(" ")}>{options.map(option => <button type="button" key={option} className={[styles.scope, `chip ${values.includes(option) ? 'selected' : ''}`].join(" ")} aria-pressed={values.includes(option)} onClick={() => onChange(values.includes(option) ? values.filter(v => v !== option) : [...values, option])}>{option}</button>)}</div>{values.length > 0 && <div className={[styles.scope, "selected-tags"].join(" ")} aria-label={`${label} 선택한 태그`}>{values.map(v => <button type="button" key={v} aria-label={`${label} ${v} 삭제`} onClick={() => onChange(values.filter(x => x !== v))}>{v}<span aria-hidden="true">×</span></button>)}</div>}</fieldset>; }
