package com.aiflow.service;

import com.aiflow.model.FormDefinition;

import java.util.List;
import java.util.Optional;

/**
 * 表单定义服务接口，提供表单的 CRUD、发布、启用/禁用等生命周期管理。
 */
public interface FormDefinitionService {

    /**
     * 创建新的表单定义。
     */
    FormDefinition createForm(FormDefinition form);

    /**
     * 更新已有的表单定义。
     */
    FormDefinition updateForm(Long id, FormDefinition form);

    /**
     * 发布表单，使其可被流程模板引用。
     */
    FormDefinition publishForm(Long id);

    /**
     * 查询所有表单定义。
     */
    List<FormDefinition> listForms();

    /**
     * 查询所有已发布的表单。
     */
    List<FormDefinition> listPublishedForms();

    /**
     * 根据ID查询表单。
     */
    Optional<FormDefinition> findById(Long id);

    /**
     * 根据ID查询处于活跃状态的表单。
     */
    Optional<FormDefinition> findActiveById(Long id);

    /**
     * 禁用指定表单。
     */
    void disableForm(Long id);
}
