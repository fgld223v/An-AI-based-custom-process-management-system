package com.aiflow.config;

import com.aiflow.enums.ProcessResourceType;
import com.aiflow.enums.TemplateSourceType;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.model.SysUser;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Order(90)
@Component
@RequiredArgsConstructor
public class ProcessResourceTypeInitializer implements CommandLineRunner {

    private final ProcessTemplateRepository processTemplateRepository;
    private final SysUserRepository sysUserRepository;

    @Override
    @Transactional
    public void run(String... args) {
        List<ProcessTemplate> legacyResources = processTemplateRepository.findByResourceTypeIsNullAndDeleted(0);
        if (legacyResources.isEmpty()) {
            return;
        }

        for (ProcessTemplate resource : legacyResources) {
            resource.setResourceType(resolveLegacyType(resource));
        }
        processTemplateRepository.saveAll(legacyResources);
        log.info("已回填 {} 条历史流程资源的 resource_type", legacyResources.size());
    }

    private ProcessResourceType resolveLegacyType(ProcessTemplate resource) {
        if (resource.getSourceType() == TemplateSourceType.MARKET_COPY) {
            return ProcessResourceType.BUSINESS_PROCESS;
        }
        if (resource.getCreatedBy() == null) {
            return ProcessResourceType.SYSTEM_TEMPLATE;
        }
        return sysUserRepository.findById(resource.getCreatedBy())
                .map(SysUser::getSystemRole)
                .filter("biz_admin"::equals)
                .map(role -> ProcessResourceType.BUSINESS_PROCESS)
                .orElse(ProcessResourceType.SYSTEM_TEMPLATE);
    }
}
