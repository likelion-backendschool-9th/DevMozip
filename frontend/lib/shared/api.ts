import axios from 'axios';
// 향후 API 교체 지점. 현재 UI에서는 이 인스턴스를 사용하지 않습니다.
export const api = axios.create({ baseURL: process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api/v1', timeout: 10000 });
