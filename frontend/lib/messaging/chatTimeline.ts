/** 프로젝트 투표의 LocalDateTime과 채팅의 offset 시각을 동일한 한국 시각 기준으로 비교한다. */
export function chatTimelineTime(value:string){
 const normalized=value.replace(/(\.\d{3})\d+/,'$1');
 const time=Date.parse(/(?:Z|[+-]\d{2}:\d{2})$/i.test(normalized)?normalized:normalized+'+09:00');
 return Number.isFinite(time)?time:0;
}
