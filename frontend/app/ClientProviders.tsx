'use client';
import {useEffect,useState,type ReactNode} from 'react';
import {PrototypeProvider} from '@/lib/state/PrototypeProvider';
import {AuthProvider} from '@/lib/state/AuthProvider';
import {ProfileProvider} from '@/lib/state/ProfileProvider';
import {ProjectProvider} from '@/lib/state/ProjectProvider';
import {MessagingProvider} from '@/lib/state/MessagingProvider';
import {HomeProvider} from '@/lib/state/HomeProvider';
import {AppLayout} from '@/components/layout/AppLayout';
// Keep the server HTML and first browser render identical. Mount storage-backed
// providers together afterwards, before any domain persistence effect can run.
export function ClientProviders({children}:{children:ReactNode}){const [ready,setReady]=useState(false);useEffect(()=>setReady(true),[]);if(!ready)return <div className="client-loading" role="status">DevMozip 데모 데이터를 준비하고 있어요…</div>;return <PrototypeProvider><AuthProvider><ProfileProvider><ProjectProvider><MessagingProvider><HomeProvider><AppLayout>{children}</AppLayout></HomeProvider></MessagingProvider></ProjectProvider></ProfileProvider></AuthProvider></PrototypeProvider>;}
