import { Suspense } from 'react';
import { connection } from 'next/server';
import CreateProjectPage from './_components/CreateProjectPage';

async function ProjectCreationMode() {
    await connection();
    return <CreateProjectPage apiEnabled={false} />;
}
export default function Page() {
    return <Suspense fallback={<p role="status">작성 화면을 준비하고 있습니다.</p>}><ProjectCreationMode /></Suspense>;
}
