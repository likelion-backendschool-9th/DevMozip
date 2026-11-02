import type {Metadata} from 'next';
import {SignupPage} from './_components/SignupScreen';
export const metadata:Metadata={title:'회원가입'};
export default function Page(){return <SignupPage/>;}
