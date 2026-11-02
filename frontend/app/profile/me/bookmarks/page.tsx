import type {Metadata} from 'next';
import {BookmarksPage} from './_components/BookmarksScreen';
export const metadata:Metadata={title:'내 북마크함'};
export default function Page(){return <BookmarksPage/>;}
