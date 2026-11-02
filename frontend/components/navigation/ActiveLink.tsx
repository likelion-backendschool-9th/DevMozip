'use client';
import Link from 'next/link';
import {usePathname} from 'next/navigation';
import type {ComponentProps} from 'react';
type Props=ComponentProps<typeof Link>&{end?:boolean};
export function ActiveLink({href,end=false,className='',...props}:Props){const pathname=usePathname();const target=String(href).split('?')[0];const active=end?pathname===target:pathname===target||target!=='/'&&pathname.startsWith(target+'/');return <Link {...props} href={href} aria-current={active?'page':undefined} className={[className,active?'active':''].filter(Boolean).join(' ')}/>;}
