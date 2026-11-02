export const introductionPolicy={maxLength:20000};
export const introductionFormats=[{value:'text',label:'일반 텍스트'},{value:'html',label:'HTML'}] as const;
export const introductionTools=[
 {label:'소제목',before:'<h2>',after:'</h2>',placeholder:'나를 소개합니다'},
 {label:'굵게',before:'<strong>',after:'</strong>',placeholder:'강조할 내용'},
 {label:'기울임',before:'<em>',after:'</em>',placeholder:'강조할 내용'},
 {label:'목록',before:'<ul>\n  <li>',after:'</li>\n</ul>',placeholder:'함께하고 싶은 일'},
 {label:'링크',before:'<a href="https://example.com">',after:'</a>',placeholder:'링크 이름'},
 {label:'구분선',before:'<hr />',after:'',placeholder:''}
];
export const introductionHint='제목·강조·목록·표·이미지·링크와 글자 색상·크기·정렬을 사용할 수 있어요. 스크립트·이벤트·외부 스타일은 제외됩니다.';
