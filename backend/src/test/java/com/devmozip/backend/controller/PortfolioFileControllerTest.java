package com.devmozip.backend.controller;

import com.devmozip.backend.service.PortfolioFileService;
import com.devmozip.backend.service.PortfolioFileService.FileContent;
import com.devmozip.backend.service.PortfolioFileService.FileLocation;
import com.devmozip.backend.storage.LocalPortfolioFileStorage.Metadata;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** nginx가 있을 때(FILE_ACCEL_REDIRECT=true)와 없을 때 파일 응답 (docs/portfolio-files-api.md). 권한 확인은 두 경우 모두 Service가 한다. */
class PortfolioFileControllerTest {
    static final String ID = "portfolio_5_20261111093000";
    PortfolioFileService service = mock(PortfolioFileService.class);
    Metadata pdf = new Metadata(ID, 5, "자료.pdf", "application/pdf", 3);
    @Test void withNginxSendsOnlyHeadersAndInternalLocationAfterPermissionCheck() {
        when(service.locate("owner@test.local", ID)).thenReturn(new FileLocation(pdf, ID + ".pdf"));
        var response = new PortfolioFileController(service, true).read(new UsernamePasswordAuthenticationToken("owner@test.local", null), ID);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("/internal-files/" + ID + ".pdf", response.getHeaders().getFirst("X-Accel-Redirect"));
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").startsWith("attachment;"));
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertNull(response.getBody());
        verify(service, never()).read(any(), any());
    }
    @Test void withoutNginxSendsBytes() {
        when(service.read(null, ID)).thenReturn(new FileContent(pdf, "%PD".getBytes()));
        var response = new PortfolioFileController(service, false).read(null, ID);
        assertArrayEquals("%PD".getBytes(), response.getBody());
        assertNull(response.getHeaders().getFirst("X-Accel-Redirect"));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        verify(service, never()).locate(any(), any());
    }
}
