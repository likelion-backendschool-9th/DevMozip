import type {Metadata} from 'next';
import {PasswordRecoveryPage} from './_components/PasswordRecoveryScreen';
export const metadata:Metadata={title:'비밀번호 찾기'};
export default function Page(){return <PasswordRecoveryPage/>;}
