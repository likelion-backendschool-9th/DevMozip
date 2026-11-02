'use client';
import styles from './Button.module.css';
import type { ButtonHTMLAttributes } from 'react';
export function Button({className='',variant='primary',...props}:ButtonHTMLAttributes<HTMLButtonElement>&{variant?:'primary'|'secondary'}){return <button className={`${styles.control} button ${variant==='secondary'?'secondary':''} ${className}`} {...props}/>;}
