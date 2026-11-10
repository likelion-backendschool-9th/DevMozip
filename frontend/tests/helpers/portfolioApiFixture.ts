import type { PortfolioDetail, PortfolioPage } from '@/types/portfolioApi';
export const portfolioDetailFixture = (): PortfolioDetail => ({
    portfolio: { id: 11, userId: 3, title: '포트폴리오 제목', thumbnailUrl: '/cover.png', topic: 'AI', summary: '기본 요약',
        role: '백엔드', techStack: ['Java'], visibility: 'PUBLIC', createdAt: '2026-11-01T12:00:00', updatedAt: null },
    author: { id: 3, nickname: '작성자', tag: 'api-developer', profileImage: null },
    description: '<script>unsafe</script>\n프로젝트 설명', attachments: [{ name: '설계', url: 'https://example.com/design.pdf' }],
});
export const portfolioPageFixture = (): PortfolioPage => ({ items: [portfolioDetailFixture().portfolio], page: 0, size: 20, totalElements: 1, totalPages: 1 });
