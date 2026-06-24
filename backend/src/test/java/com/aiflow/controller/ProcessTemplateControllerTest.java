package com.aiflow.controller;

import com.aiflow.enums.ProcessResourceType;
import com.aiflow.enums.TemplateStatus;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.service.ProcessTemplateService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessTemplateControllerTest {

    private final ProcessTemplateService service = mock(ProcessTemplateService.class);
    private final ProcessTemplateController controller = new ProcessTemplateController(service);

    @Test
    void deletesSystemTemplateVersion() {
        when(service.findById(3L)).thenReturn(Optional.of(template(ProcessResourceType.SYSTEM_TEMPLATE)));

        controller.deleteTemplate(3L);

        verify(service).deleteTemplate(3L);
    }

    @Test
    void doesNotDeleteBusinessProcessThroughSystemTemplateEndpoint() {
        when(service.findById(3L)).thenReturn(Optional.of(template(ProcessResourceType.BUSINESS_PROCESS)));

        assertThatThrownBy(() -> controller.deleteTemplate(3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("system template");
        verify(service, never()).deleteTemplate(3L);
    }

    private ProcessTemplate template(ProcessResourceType resourceType) {
        return ProcessTemplate.builder()
                .id(3L)
                .resourceType(resourceType)
                .status(TemplateStatus.DRAFT)
                .deleted(0)
                .build();
    }
}
