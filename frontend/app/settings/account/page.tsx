import type {Metadata} from 'next';
import {AccountSettingsPage} from './_components/AccountSettingsScreen';
export const metadata:Metadata={title:'개인정보 및 계정 설정'};
export default function Page(){return <AccountSettingsPage/>;}
