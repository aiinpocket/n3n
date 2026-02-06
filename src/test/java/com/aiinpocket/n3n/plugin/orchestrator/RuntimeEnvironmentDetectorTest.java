package com.aiinpocket.n3n.plugin.orchestrator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RuntimeEnvironmentDetectorTest {

    private final RuntimeEnvironmentDetector detector = new RuntimeEnvironmentDetector();

    @Test
    void detect_returnsNonNullResult() {
        // detect() should always return a valid enum value
        RuntimeEnvironmentDetector.RuntimeEnvironment result = detector.detect();
        assertThat(result).isNotNull();
        assertThat(result).isIn(
                RuntimeEnvironmentDetector.RuntimeEnvironment.KUBERNETES,
                RuntimeEnvironmentDetector.RuntimeEnvironment.DOCKER,
                RuntimeEnvironmentDetector.RuntimeEnvironment.UNKNOWN
        );
    }

    @Test
    void detect_onLocalMachine_returnsDockerOrUnknown() {
        // On a local dev machine (not in K8s pod), should return DOCKER or UNKNOWN
        RuntimeEnvironmentDetector.RuntimeEnvironment result = detector.detect();
        // We're not running in K8s during tests
        assertThat(result).isNotEqualTo(RuntimeEnvironmentDetector.RuntimeEnvironment.KUBERNETES);
    }

    @Test
    void runtimeEnvironment_enumHasExpectedValues() {
        assertThat(RuntimeEnvironmentDetector.RuntimeEnvironment.values()).hasSize(3);
        assertThat(RuntimeEnvironmentDetector.RuntimeEnvironment.valueOf("KUBERNETES")).isNotNull();
        assertThat(RuntimeEnvironmentDetector.RuntimeEnvironment.valueOf("DOCKER")).isNotNull();
        assertThat(RuntimeEnvironmentDetector.RuntimeEnvironment.valueOf("UNKNOWN")).isNotNull();
    }
}
