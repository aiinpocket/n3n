package com.aiinpocket.n3n.hostedapp.service;

import com.aiinpocket.n3n.hostedapp.config.HostedAppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 埠配置：跳過 DB 保留埠與主機占用埠，範圍耗盡時明確報錯。
 */
class AppPortAllocatorTest {

    private AppPortAllocator allocator;

    @BeforeEach
    void setUp() {
        HostedAppProperties properties = Mockito.mock(HostedAppProperties.class);
        when(properties.getPortRangeStart()).thenReturn(28000);
        when(properties.getPortRangeEnd()).thenReturn(28004);
        Mockito.lenient().when(properties.getPortRange()).thenReturn("28000-28004");
        allocator = new AppPortAllocator(properties);
    }

    @Test
    @DisplayName("依序取得第一個可用埠")
    void allocatesFirstFreePort() {
        assertThat(allocator.allocate(Set.of(), port -> true)).isEqualTo(28000);
    }

    @Test
    @DisplayName("跳過 DB 已保留的埠")
    void skipsReservedPorts() {
        assertThat(allocator.allocate(Set.of(28000, 28001), port -> true)).isEqualTo(28002);
    }

    @Test
    @DisplayName("跳過主機無法綁定的埠")
    void skipsBusyPorts() {
        assertThat(allocator.allocate(Set.of(28000), port -> port != 28001)).isEqualTo(28002);
    }

    @Test
    @DisplayName("範圍耗盡：明確錯誤")
    void exhaustedRangeThrows() {
        assertThatThrownBy(() -> allocator.allocate(Set.of(), port -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("無可用埠");
    }
}
