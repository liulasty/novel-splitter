package com.novel.splitter.assembler.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssemblerConfigTest {

    @Test
    void newConfig_hasSafeDefaults() {
        AssemblerConfig c = new AssemblerConfig();
        assertEquals(0.15, c.getQualityScoreWeight(), 1e-9);
        assertEquals(1, c.getExpandRadius());
        assertFalse(c.isExpandAcrossChapters());
    }
}
