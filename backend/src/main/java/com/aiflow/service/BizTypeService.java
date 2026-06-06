package com.aiflow.service;

import com.aiflow.model.BizTypeDict;

import java.util.List;
import java.util.Optional;

public interface BizTypeService {

    List<BizTypeDict> listEnabledBizTypes();

    Optional<BizTypeDict> findByTypeCode(String typeCode);
}
