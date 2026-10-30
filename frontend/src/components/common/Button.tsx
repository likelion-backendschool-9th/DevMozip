import type { ButtonHTMLAttributes } from 'react';
export function Button({className='',variant='primary',...props}:ButtonHTMLAttributes<HTMLButtonElement>&{variant?:'primary'|'secondary'}){return <button className={`button ${variant==='secondary'?'secondary':''} ${className}`} {...props}/>;}
