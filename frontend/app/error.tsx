'use client';
export default function ErrorPage({reset}:{error:Error&{digest?:string};reset:()=>void}){return <section className="container planned"><h1>화면을 불러오지 못했어요.</h1><p>잠시 후 다시 시도해 주세요. 저장된 Mock 데이터는 유지됩니다.</p><button className="button" onClick={reset}>다시 시도</button></section>;}
