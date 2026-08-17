package com.aiinpocket.n3n;

import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import com.aiinpocket.n3n.template.dto.OfficialTemplateDto;
import com.aiinpocket.n3n.template.service.FlowTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class N3nApplicationTests {

    @Autowired
    private FlowTemplateService flowTemplateService;

    @Autowired
    private NodeHandlerRegistry nodeHandlerRegistry;

    @Test
    void contextLoads() {
    }

    /**
     * 全部 58 個內建範本都必須可用（節點齊備）。
     * 這是「範本是活的入口，不是死路」的守門測試：
     * 有人新增範本用了不存在的節點、或改壞 alias 對應時，這裡會先擋下。
     */
    @Test
    @SuppressWarnings("unchecked")
    void everyOfficialTemplateIsUsable() {
        List<OfficialTemplateDto> templates = flowTemplateService.getOfficialTemplates();
        assertThat(templates).hasSize(58);

        for (OfficialTemplateDto template : templates) {
            List<Map<String, Object>> nodes =
                    (List<Map<String, Object>>) template.getDefinition().get("nodes");
            for (Map<String, Object> node : nodes) {
                String type = String.valueOf(node.get("type"));
                assertThat(nodeHandlerRegistry.hasHandler(type))
                        .as("template %s uses node type %s", template.getId(), type)
                        .isTrue();
            }
        }
    }
}
