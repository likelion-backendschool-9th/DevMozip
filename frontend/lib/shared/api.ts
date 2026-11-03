import axios from 'axios';
// 향후 자원 API도 동일 출처 Route Handler를 통해 호출한다.
export const api = axios.create({ baseURL: '/api', timeout: 10000 });
