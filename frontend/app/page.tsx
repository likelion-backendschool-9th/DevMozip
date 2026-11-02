import type {Metadata} from 'next';
import {HomePage} from './_components/HomeScreen';
export const metadata:Metadata={title:'홈'};
export default function Page(){return <HomePage/>;}
