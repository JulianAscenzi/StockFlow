package com.julianas.stockflow.common.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesReturnsAndScopesAnIdForEveryRequestOnTheSameThread() throws Exception {
        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), first, (request, response) -> {
            assertThat(MDC.get("requestId")).isEqualTo(first.getHeader("X-Request-ID"));
            assertThat(UUID.fromString(MDC.get("requestId"))).isNotNull();
        });
        assertThat(MDC.get("requestId")).isNull();
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), second, (request, response) ->
                assertThat(MDC.get("requestId")).isEqualTo(second.getHeader("X-Request-ID")));
        assertThat(second.getHeader("X-Request-ID")).isNotEqualTo(first.getHeader("X-Request-ID"));
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void reusesValidIdAndPreservesOtherMdcFields() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "edge-123_test.4");
        MDC.put("traceId", "outer-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("requestId")).isEqualTo("edge-123_test.4");
            assertThat(MDC.get("traceId")).isEqualTo("outer-trace");
        });
        assertThat(response.getHeader("X-Request-ID")).isEqualTo("edge-123_test.4");
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("traceId")).isEqualTo("outer-trace");
    }

    @Test
    void replacesMalformedOversizedAndDuplicateIds() throws Exception {
        for (String value : new String[]{"", "a\r\nforged", "with spaces", "a".repeat(65)}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Request-ID", value);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> {});
            assertThat(UUID.fromString(response.getHeader("X-Request-ID"))).isNotNull();
        }
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", new String[]{"one", "two"});
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        assertThat(UUID.fromString(response.getHeader("X-Request-ID"))).isNotNull();
    }

    @Test
    void restoresMdcEvenWhenTheChainFails() {
        MDC.put("requestId", "outer-scope");
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> { throw new ServletException("failure"); })).isInstanceOf(ServletException.class);
        assertThat(MDC.get("requestId")).isEqualTo("outer-scope");
    }

    @Test
    void logsInsideTheRequestAutomaticallyContainItsId() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestIdFilterTest.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Request-ID", "logged-request");
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> logger.info("Request test event"));
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getMDCPropertyMap()).containsEntry("requestId", "logged-request");
            assertThat(MDC.get("requestId")).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void asyncAndErrorRedispatchesKeepTheOriginalIdAndCleanTheirThread() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        String id = response.getHeader("X-Request-ID");
        for (DispatcherType dispatcher : new DispatcherType[]{DispatcherType.ASYNC, DispatcherType.ERROR}) {
            request.setDispatcherType(dispatcher);
            filter.doFilter(request, response, (req, res) -> assertThat(MDC.get("requestId")).isEqualTo(id));
            assertThat(response.getHeader("X-Request-ID")).isEqualTo(id);
            assertThat(MDC.get("requestId")).isNull();
        }
    }
}
