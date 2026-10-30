import type { HTMLAttributes } from 'react';
export function Card({className='',...props}:HTMLAttributes<HTMLElement>){return <article className={`surface-card ${className}`} {...props}/>;}
