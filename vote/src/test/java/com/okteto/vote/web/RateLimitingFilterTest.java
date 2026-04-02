package com.okteto.vote.web;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

class RateLimitingFilterTest {

    @Test
    void blocksRequestsAfterDefaultLimitInSameWindow() throws Exception {
        OncePerRequestFilter filter = new RateLimitingFilter();

        for (int i = 0; i < 60; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/");
            request.addHeader("X-Forwarded-For", "203.0.113.10");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isNotEqualTo(429);
        }

        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", "/");
        blockedRequest.addHeader("X-Forwarded-For", "203.0.113.10");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(blockedRequest, blockedResponse, chain);

        assertThat(blockedResponse.getStatus()).isEqualTo(429);
        assertThat(blockedResponse.getHeader("Retry-After")).isNotBlank();
    }

    @Test
    void doesNotLimitGetRequestsToRoot() throws Exception {
        OncePerRequestFilter filter = new RateLimitingFilter();

        for (int i = 0; i < 80; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
            request.addHeader("X-Forwarded-For", "198.51.100.20");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void limitsPerClientIndependently() throws Exception {
        OncePerRequestFilter filter = new RateLimitingFilter();

        for (int i = 0; i < 61; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/");
            request.addHeader("X-Forwarded-For", "203.0.113.10");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
        }

        MockHttpServletRequest otherClientRequest = new MockHttpServletRequest("POST", "/");
        otherClientRequest.addHeader("X-Forwarded-For", "198.51.100.99");
        MockHttpServletResponse otherClientResponse = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(otherClientRequest, otherClientResponse, chain);

        assertThat(otherClientResponse.getStatus()).isNotEqualTo(429);
    }

    @Test
    void usesFirstIpFromXForwardedForAsClientKey() throws Exception {
        OncePerRequestFilter filter = new RateLimitingFilter();

        for (int i = 0; i < 61; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/");
            request.addHeader("X-Forwarded-For", "203.0.113.10, 198.51.100.20");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
        }

        MockHttpServletRequest sameFirstIp = new MockHttpServletRequest("POST", "/");
        sameFirstIp.addHeader("X-Forwarded-For", "203.0.113.10");
        MockHttpServletResponse sameFirstIpResponse = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(sameFirstIp, sameFirstIpResponse, chain);

        MockHttpServletRequest differentFirstIp = new MockHttpServletRequest("POST", "/");
        differentFirstIp.addHeader("X-Forwarded-For", "198.51.100.20, 203.0.113.10");
        MockHttpServletResponse differentFirstIpResponse = new MockHttpServletResponse();
        chain = new MockFilterChain();
        filter.doFilter(differentFirstIp, differentFirstIpResponse, chain);

        assertThat(sameFirstIpResponse.getStatus()).isEqualTo(429);
        assertThat(differentFirstIpResponse.getStatus()).isNotEqualTo(429);
    }

    @Test
    void allowsRequestsAgainAfterWindowExpires() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();

        for (int i = 0; i < 61; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/");
            request.addHeader("X-Forwarded-For", "203.0.113.10");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
        }

        Field windowsField = RateLimitingFilter.class.getDeclaredField("windowsByClient");
        windowsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Object> windows = (ConcurrentHashMap<String, Object>) windowsField.get(filter);
        Object window = windows.get("203.0.113.10");

        Field windowStartField = window.getClass().getDeclaredField("windowStartMs");
        windowStartField.setAccessible(true);
        windowStartField.setLong(window, 0L);

        MockHttpServletRequest requestAfterWindow = new MockHttpServletRequest("POST", "/");
        requestAfterWindow.addHeader("X-Forwarded-For", "203.0.113.10");
        MockHttpServletResponse responseAfterWindow = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestAfterWindow, responseAfterWindow, chain);

        assertThat(responseAfterWindow.getStatus()).isNotEqualTo(429);
    }
}
