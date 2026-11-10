import {it,expect} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';
import {ChatWindow} from './ChatWindow';
import type {ChatMessage} from '@/types/messaging';
const message=(id:string,createdAt:string):ChatMessage=>({id,body:id,senderId:'sender',kind:'text',createdAt,readBy:[]});
it('투표 생성 시각에 카드를 삽입하고 이후 메시지는 그 아래에 표시한다',()=>{
 const render=(messages:ChatMessage[])=>renderToStaticMarkup(<ChatWindow channelKey="project" members={[]} messages={messages} onSend={()=>({ok:true})} historyItems={[{id:'vote',createdAt:'2026-11-11T10:00:00.123456',content:<div>투표카드</div>}]}/>);
 const before=message('이전대화','2026-11-11T00:59:00Z');const after=message('이후대화','2026-11-11T01:01:00Z');
 const html=render([before,after]);expect(html.indexOf('이전대화')).toBeLessThan(html.indexOf('투표카드'));expect(html.indexOf('투표카드')).toBeLessThan(html.indexOf('이후대화'));
 expect(render([])).not.toContain('첫 메시지로');
});
it('여러 투표도 각각의 생성 시각 순서로 표시한다',()=>{
 const html=renderToStaticMarkup(<ChatWindow channelKey="project" members={[]} messages={[message('중간대화','2026-11-11T01:01:00Z')]} onSend={()=>({ok:true})} historyItems={[{id:'later',createdAt:'2026-11-11T10:02:00',content:<div>나중투표</div>},{id:'earlier',createdAt:'2026-11-11T10:00:00',content:<div>처음투표</div>}]}/>);
 expect(html.indexOf('처음투표')).toBeLessThan(html.indexOf('중간대화'));expect(html.indexOf('중간대화')).toBeLessThan(html.indexOf('나중투표'));
});
