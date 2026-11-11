/** 서버 LocalDateTime은 한국 시각으로 해석하고, Mock의 UTC 시각도 같은 표시를 사용한다. */
export function formatKickExpiry(value:string){
 const normalized=value.replace(/(\.\d{3})\d+/,'$1');
 const date=new Date(/(?:Z|[+-]\d{2}:\d{2})$/i.test(normalized)?normalized:normalized+'+09:00');
 if(Number.isNaN(date.getTime()))return '시간 확인 필요';
 return new Intl.DateTimeFormat('ko-KR',{timeZone:'Asia/Seoul',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).format(date);
}
