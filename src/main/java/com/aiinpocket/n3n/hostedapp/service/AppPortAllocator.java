package com.aiinpocket.n3n.hostedapp.service;

import com.aiinpocket.n3n.hostedapp.config.HostedAppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Hosted App 對外埠配置：從 n3n.apps.port-range 依序找出
 * 「DB 未占用且主機可綁定」的埠。
 */
@Service
@RequiredArgsConstructor
public class AppPortAllocator {

    private final HostedAppProperties properties;

    /**
     * @param reserved 已被其他 app 占用的埠（來自 DB）
     * @return 可用的 host port
     * @throws IllegalStateException 範圍內已無可用埠
     */
    public int allocate(Set<Integer> reserved) {
        return allocate(reserved, this::isPortFree);
    }

    /** 可注入探測邏輯的版本（測試用） */
    int allocate(Set<Integer> reserved, IntPredicate free) {
        for (int port = properties.getPortRangeStart(); port <= properties.getPortRangeEnd(); port++) {
            if (!reserved.contains(port) && free.test(port)) {
                return port;
            }
        }
        throw new IllegalStateException(
                "埠範圍 " + properties.getPortRange() + " 內已無可用埠，請聯絡管理員擴大範圍");
    }

    private boolean isPortFree(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
