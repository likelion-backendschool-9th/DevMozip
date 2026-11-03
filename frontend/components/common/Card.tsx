'use client';
import styles from './Card.module.css';
import type { HTMLAttributes } from 'react';
export function Card({className='',...props}:HTMLAttributes<HTMLElement>){return <article className={`${styles.surface} surface-card ${className}`} {...props}/>;}
