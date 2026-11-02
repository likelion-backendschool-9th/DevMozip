import type {Metadata} from 'next';
import {MessagesPage} from './_components/MessagesScreen';
export const metadata:Metadata={title:'쪽지'};
export default function Page(){return <MessagesPage/>;}
