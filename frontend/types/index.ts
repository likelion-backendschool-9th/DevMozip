export interface RouteDefinition { path: string; title: string; description: string; stage: number; group: string }
export interface ApiResponse<T> { data: T; message?: string }
export interface ApiError { code: string; message: string }
