package com.devmozip.backend.security.jwt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InMemoryRefreshTokenStoreTest {

    private final InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore();

    @Test
    void 마지막으로_저장한_토큰만_인정한다() {
        store.save("member@devmozip.demo", "old");
        store.save("member@devmozip.demo", "new");

        assertFalse(store.matches("member@devmozip.demo", "old"));
        assertTrue(store.matches("member@devmozip.demo", "new"));
        assertFalse(store.matches("member@devmozip.demo", null));
    }

    @Test
    void 이전_토큰으로_로그아웃해도_새_로그인은_유지된다() {
        store.save("member@devmozip.demo", "new");

        store.removeMatching("member@devmozip.demo", "old");
        assertTrue(store.matches("member@devmozip.demo", "new"));

        store.removeMatching("member@devmozip.demo", "new");
        assertFalse(store.matches("member@devmozip.demo", "new"));
    }
}
