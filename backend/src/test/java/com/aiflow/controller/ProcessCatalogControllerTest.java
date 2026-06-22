package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.ProcessTemplateDTO;
import com.aiflow.enums.ProcessResourceType;
import com.aiflow.enums.TemplateStatus;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.service.ProcessAuthorizationService;
import com.aiflow.service.ProcessTemplateService;
import com.aiflow.service.ProcessRoutePreviewService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessCatalogControllerTest {

    private final ProcessTemplateService processTemplateService = mock(ProcessTemplateService.class);
    private final ProcessAuthorizationService processAuthorizationService = mock(ProcessAuthorizationService.class);
    private final ProcessRoutePreviewService processRoutePreviewService = mock(ProcessRoutePreviewService.class);
    private final ProcessCatalogController controller = new ProcessCatalogController(
            processTemplateService, processAuthorizationService, processRoutePreviewService);

    @Test
    void catalogOnlyReturnsProcessesCurrentUserCanStart() {
        ProcessTemplate allowed = process(1L, "Allowed process");
        ProcessTemplate denied = process(2L, "Denied process");
        when(processTemplateService.listPublishedBusinessProcesses()).thenReturn(List.of(allowed, denied));
        when(processAuthorizationService.canCurrentUserStart(allowed)).thenReturn(true);
        when(processAuthorizationService.canCurrentUserStart(denied)).thenReturn(false);

        ApiResponse<List<ProcessTemplateDTO>> response = controller.listAvailableProcesses();

        assertThat(response.getData())
                .extracting(ProcessTemplateDTO::getId)
                .containsExactly(1L);
    }

    private ProcessTemplate process(Long id, String name) {
        return ProcessTemplate.builder()
                .id(id)
                .templateCode("process-" + id)
                .templateName(name)
                .resourceType(ProcessResourceType.BUSINESS_PROCESS)
                .status(TemplateStatus.PUBLISHED)
                .build();
    }
}
