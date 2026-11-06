import { describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/shared/api', () => ({ api: { post: vi.fn() } }));
import { api } from '@/lib/shared/api';
import { createProjectViaApi, parseProjectCreationReceipt, toProjectCreationRequest } from './projectCreationApi';
import { emptyProjectInput } from '@/mocks/project';
const input = { ...emptyProjectInput, title: '프로젝트', field: '교육', stacks: ['React'], deadline: '2026-12-20', slots: [{ role: '디자이너', count: 2, minYears: 1 }] };
describe('프로젝트 생성 API 어댑터', () => {
 it('화면 직군·날짜·분야를 실제 생성 DTO로 변환하고 신원은 전달하지 않는다', () => {
  const dto = toProjectCreationRequest(input, ' https://example.test/image.png ', false);
  expect(dto.recruitmentSlots).toEqual([{ roleCode: 'DESIGNER', capacity: 2, minimumYears: 1 }]);
  expect(dto.interests).toEqual(['교육']); expect(dto.recruitmentEnd).toBe(input.deadline);
  expect(dto.imageUrl).toBe('https://example.test/image.png'); expect(dto.isPublic).toBe(false);
  expect(dto).not.toHaveProperty('hostMemberId');
 });
 it.each(['', 'javascript:alert(1)', 'data:image/png;base64,abc', '/local.png'])('잘못된 이미지 %s 거절', image => {
  expect(() => toProjectCreationRequest(input, image, true)).toThrow();
 });
 it('직접 입력 직군을 전달하고 기술 6개는 거절한다', () => {
  expect(toProjectCreationRequest({ ...input, slots: [{ role: 'SRE', count: 1, minYears: 0 }] }, 'https://example.test/a', true).recruitmentSlots[0].roleCode).toBe('SRE');
  expect(() => toProjectCreationRequest({ ...input, stacks: Array(6).fill('React') }, 'https://example.test/a', true)).toThrow();
 });
 it.each([null, {}, { id: -1, title: 'a' }, { id: '1', title: 'a' }])('잘못된 등록 결과를 성공으로 취급하지 않는다', value => {
  expect(() => parseProjectCreationReceipt(value)).toThrow();
 });
 it('동일 출처 BFF로 전송하며 성공 응답을 반환한다', async () => {
  vi.mocked(api.post).mockResolvedValue({ data: { id: 10, title: '프로젝트' } });
  const dto = toProjectCreationRequest(input, 'https://example.test/a', true);
  expect(await createProjectViaApi(dto)).toEqual({ id: 10, title: '프로젝트' });
  expect(api.post).toHaveBeenCalledWith('/projects', dto);
 });
 it('실패를 Mock 저장으로 대체하지 않는다', async () => {
  vi.mocked(api.post).mockRejectedValue(new Error('offline'));
  await expect(createProjectViaApi(toProjectCreationRequest(input, 'https://example.test/a', true))).rejects.toThrow('offline');
 });
});
