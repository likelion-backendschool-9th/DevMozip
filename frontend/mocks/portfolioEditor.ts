import type {PortfolioBlock} from '@/types/profile';
export const portfolioEditorPolicy={maxBlocks:20,maxContentLength:20000,maxTitleLength:80};
export const portfolioBlockTemplates:Omit<PortfolioBlock,'content'>[]=[
 {id:'description',kind:'text',title:'프로젝트 설명',format:'text',source:'description'},
 {id:'contribution',kind:'text',title:'나의 기여',format:'text',source:'contribution'},
 {id:'features',kind:'text',title:'주요 구현 기능',format:'text',source:'features'},
 {id:'challenge',kind:'text',title:'문제 해결 경험',format:'text',source:'challenge'},
 {id:'outcome',kind:'text',title:'개발 과정 및 결과물',format:'text',source:'outcome'},
 {id:'gallery',kind:'gallery',title:'프로젝트 이미지',format:'text'}
];
export const portfolioBlockFormats=[{value:'text',label:'일반 텍스트'},{value:'markdown',label:'Markdown'},{value:'html',label:'HTML'}] as const;
export const portfolioFormattingTools=[{label:'소제목',markdown:['## ',''],html:['<h3>','</h3>']},{label:'강조',markdown:['**','**'],html:['<strong>','</strong>']},{label:'목록',markdown:['- ',''],html:['<ul><li>','</li></ul>']},{label:'코드',markdown:['```typescript\n','\n```'],html:['<pre><code>','</code></pre>']},{label:'링크',markdown:['[','](https://example.com)'],html:['<a href="https://example.com">','</a>']}] as const;
export const portfolioDragInstructions={draggable:'스페이스 키로 섹션을 집고 방향키로 이동하세요. 스페이스 키로 놓거나 Escape 키로 취소합니다.'};

export const portfolioFieldOptions=['웹 서비스','모바일','AI / 머신러닝','데이터','게임','보안','클라우드 / 인프라','생산성','교육','핀테크','커뮤니티','디자인 시스템','개발 기록'];
