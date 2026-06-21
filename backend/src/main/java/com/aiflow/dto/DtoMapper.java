package com.aiflow.dto;

import com.aiflow.enums.DatabaseEnum;
import com.aiflow.enums.TemplateSourceType;
import com.aiflow.enums.ProcessResourceType;
import com.aiflow.model.BizTypeDict;
import com.aiflow.model.FormDefinition;
import com.aiflow.model.ProcessFragment;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.model.TemplateMarket;

public final class DtoMapper {

    private DtoMapper() {
    }

    public static BizTypeDTO toBizTypeDTO(BizTypeDict entity) {
        if (entity == null) {
            return null;
        }
        return BizTypeDTO.builder()
                .id(entity.getId())
                .parentId(entity.getParentId())
                .typeCode(entity.getTypeCode())
                .typeName(entity.getTypeName())
                .description(entity.getDescription())
                .sortOrder(entity.getSortOrder())
                .build();
    }

    public static FormDefinitionDTO toFormDefinitionDTO(FormDefinition entity) {
        if (entity == null) {
            return null;
        }
        return FormDefinitionDTO.builder()
                .id(entity.getId())
                .formCode(entity.getFormCode())
                .formName(entity.getFormName())
                .bizTypeId(entity.getBizTypeId())
                .version(entity.getVersion())
                .status(enumValue(entity.getStatus()))
                .fieldList(entity.getFieldList())
                .formSchema(entity.getFormSchema())
                .publishedAt(entity.getPublishedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static FormDefinition toFormDefinition(FormCreateRequest request) {
        if (request == null) {
            return null;
        }
        return FormDefinition.builder()
                .formCode(request.getFormCode())
                .formName(request.getFormName())
                .bizTypeId(request.getBizTypeId())
                .version(request.getVersion())
                .fieldList(request.getFieldList())
                .formSchema(request.getFormSchema())
                .build();
    }

    public static FormDefinition toFormDefinition(FormUpdateRequest request) {
        if (request == null) {
            return null;
        }
        return FormDefinition.builder()
                .formName(request.getFormName())
                .bizTypeId(request.getBizTypeId())
                .fieldList(request.getFieldList())
                .formSchema(request.getFormSchema())
                .build();
    }

    public static ProcessTemplateDTO toProcessTemplateDTO(ProcessTemplate entity) {
        if (entity == null) {
            return null;
        }
        return ProcessTemplateDTO.builder()
                .id(entity.getId())
                .templateCode(entity.getTemplateCode())
                .templateName(entity.getTemplateName())
                .bizTypeId(entity.getBizTypeId())
                .formId(entity.getFormId())
                .version(entity.getVersion())
                .status(enumValue(entity.getStatus()))
                .sourceType(enumValue(entity.getSourceType()))
                .resourceType(enumValue(entity.getResourceType()))
                .bpmnXml(entity.getBpmnXml())
                .nodeConfig(entity.getNodeConfig())
                .formBindConfig(entity.getFormBindConfig())
                .flowableDeploymentId(entity.getFlowableDeploymentId())
                .flowableProcessDefinitionId(entity.getFlowableProcessDefinitionId())
                .createdBy(entity.getCreatedBy())
                .publishedAt(entity.getPublishedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static ProcessTemplate toProcessTemplate(ProcessTemplateCreateRequest request) {
        if (request == null) {
            return null;
        }
        return ProcessTemplate.builder()
                .templateCode(request.getTemplateCode())
                .templateName(request.getTemplateName())
                .bizTypeId(request.getBizTypeId())
                .formId(request.getFormId())
                .sourceType(parseTemplateSourceType(request.getSourceType()))
                .resourceType(parseProcessResourceType(request.getResourceType()))
                .bpmnXml(request.getBpmnXml())
                .nodeConfig(request.getNodeConfig())
                .formBindConfig(request.getFormBindConfig())
                .createdBy(request.getCreatedBy())
                .build();
    }

    public static ProcessTemplate toProcessTemplate(ProcessTemplateUpdateRequest request) {
        if (request == null) {
            return null;
        }
        return ProcessTemplate.builder()
                .templateName(request.getTemplateName())
                .bizTypeId(request.getBizTypeId())
                .formId(request.getFormId())
                .bpmnXml(request.getBpmnXml())
                .nodeConfig(request.getNodeConfig())
                .formBindConfig(request.getFormBindConfig())
                .build();
    }

    public static ProcessFragmentDTO toProcessFragmentDTO(ProcessFragment entity) {
        if (entity == null) {
            return null;
        }
        return ProcessFragmentDTO.builder()
                .id(entity.getId())
                .fragmentCode(entity.getFragmentCode())
                .fragmentName(entity.getFragmentName())
                .bizTypeId(entity.getBizTypeId())
                .description(entity.getDescription())
                .fragmentType(entity.getFragmentType())
                .status(enumValue(entity.getStatus()))
                .bpmnXml(entity.getBpmnXml())
                .nodeConfig(entity.getNodeConfig())
                .createdBy(entity.getCreatedBy())
                .publishedAt(entity.getPublishedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static ProcessFragment toProcessFragment(ProcessFragmentCreateRequest request) {
        if (request == null) {
            return null;
        }
        return ProcessFragment.builder()
                .fragmentCode(request.getFragmentCode())
                .fragmentName(request.getFragmentName())
                .bizTypeId(request.getBizTypeId())
                .description(request.getDescription())
                .fragmentType(request.getFragmentType())
                .bpmnXml(request.getBpmnXml())
                .nodeConfig(request.getNodeConfig())
                .createdBy(request.getCreatedBy())
                .build();
    }

    public static ProcessFragment toProcessFragment(ProcessFragmentUpdateRequest request) {
        if (request == null) {
            return null;
        }
        return ProcessFragment.builder()
                .fragmentName(request.getFragmentName())
                .bizTypeId(request.getBizTypeId())
                .description(request.getDescription())
                .fragmentType(request.getFragmentType())
                .bpmnXml(request.getBpmnXml())
                .nodeConfig(request.getNodeConfig())
                .build();
    }

    public static TemplateMarketDTO toTemplateMarketDTO(TemplateMarket entity) {
        if (entity == null) {
            return null;
        }
        return TemplateMarketDTO.builder()
                .id(entity.getId())
                .sourceId(entity.getSourceId())
                .type(enumValue(entity.getType()))
                .title(entity.getTitle())
                .description(entity.getDescription())
                .coverUrl(entity.getCoverUrl())
                .bizTypeId(entity.getBizTypeId())
                .publisherId(entity.getPublisherId())
                .useCount(entity.getUseCount())
                .rating(entity.getRating())
                .tags(entity.getTags())
                .publishedAt(entity.getPublishedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private static TemplateSourceType parseTemplateSourceType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalizedValue = value.trim();
        for (TemplateSourceType item : TemplateSourceType.values()) {
            if (item.name().equalsIgnoreCase(normalizedValue) || item.getValue().equalsIgnoreCase(normalizedValue)) {
                return item;
            }
        }
        throw new IllegalArgumentException("invalid sourceType: " + value);
    }

    private static ProcessResourceType parseProcessResourceType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalizedValue = value.trim();
        for (ProcessResourceType item : ProcessResourceType.values()) {
            if (item.name().equalsIgnoreCase(normalizedValue) || item.getValue().equalsIgnoreCase(normalizedValue)) {
                return item;
            }
        }
        throw new IllegalArgumentException("invalid resourceType: " + value);
    }

    private static String enumValue(Enum<?> value) {
        if (value == null) {
            return null;
        }
        if (value instanceof DatabaseEnum databaseEnum) {
            return databaseEnum.getValue();
        }
        return value.name();
    }
}
