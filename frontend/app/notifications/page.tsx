import type {Metadata} from 'next';
import {NotificationsPage} from './_components/NotificationsScreen';
export const metadata:Metadata={title:'알림'};
export default function Page(){return <NotificationsPage/>;}
