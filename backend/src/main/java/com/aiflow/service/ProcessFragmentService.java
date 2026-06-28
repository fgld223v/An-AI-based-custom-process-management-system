package com.aiflow.service;

import com.aiflow.model.ProcessFragment;

import java.util.List;
import java.util.Optional;

/**
 * 流程片段服务接口，提供可复用流程片段的创建、编辑、发布及查询能力。
 */
public interface ProcessFragmentService {

    /**
     * 创建新的流程片段。
     */
    ProcessFragment createFragment(ProcessFragment fragment);

    /**
     * 更新已有流程片段。
     */
    ProcessFragment updateFragment(Long id, ProcessFragment fragment);

    /**
     * 发布流程片段使其可被模板引用。
     */
    ProcessFragment publishFragment(Long id);

    /**
     * 查询所有流程片段。
     */
    List<ProcessFragment> listFragments();

    /**
     * 根据ID查询流程片段。
     */
    Optional<ProcessFragment> findById(Long id);
}
