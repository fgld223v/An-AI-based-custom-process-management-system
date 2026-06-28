package com.aiflow.service;

import com.aiflow.model.BizTypeDict;

import java.util.List;
import java.util.Optional;

/**
 * 业务类型字典服务接口，提供业务类型的查询能力。
 */
public interface BizTypeService {

    /**
     * 查询所有启用状态的业务类型。
     *
     * @return 已启用的业务类型列表
     */
    List<BizTypeDict> listEnabledBizTypes();

    /**
     * 根据业务类型编码查询。
     *
     * @param typeCode 业务类型编码
     * @return 匹配的业务类型，不存在时返回 empty
     */
    Optional<BizTypeDict> findByTypeCode(String typeCode);
}
