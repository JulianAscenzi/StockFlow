package com.julianas.stockflow.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PageResponseTest {

    @Test
    void preservesPageContentAndMetadata() {
        Page<String> page = new PageImpl<>(List.of("one", "two"), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertEquals(List.of("one", "two"), response.content());
        assertEquals(1, response.page());
        assertEquals(2, response.size());
        assertEquals(5, response.totalElements());
        assertEquals(3, response.totalPages());
        assertFalse(response.first());
        assertFalse(response.last());
    }

    @Test
    void handlesEmptyPage() {
        PageResponse<String> response = PageResponse.from(Page.empty(PageRequest.of(0, 20)));

        assertTrue(response.content().isEmpty());
        assertEquals(0, response.page());
        assertEquals(20, response.size());
        assertEquals(0, response.totalElements());
        assertEquals(0, response.totalPages());
        assertTrue(response.first());
        assertTrue(response.last());
    }

    @Test
    void exposesUnmodifiableContent() {
        PageResponse<String> response = PageResponse.from(new PageImpl<>(List.of("one")));

        assertThrows(UnsupportedOperationException.class, () -> response.content().add("two"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void doesNotReuseMutableListProvidedByPage() {
        List<String> mutableContent = new ArrayList<>(List.of("one"));
        Page<String> page = mock(Page.class);
        when(page.getContent()).thenReturn(mutableContent);

        PageResponse<String> response = PageResponse.from(page);
        mutableContent.add("two");

        assertNotSame(mutableContent, response.content());
        assertEquals(List.of("one"), response.content());
    }
}
