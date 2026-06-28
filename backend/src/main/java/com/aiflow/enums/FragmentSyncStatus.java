package com.aiflow.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 流程片段同步状态枚举：已同步、待更新、已解绑。
 */
@Getter
@AllArgsConstructor
public enum FragmentSyncStatus implements DatabaseEnum {
    SYNCED("synced"),
    PENDING_UPDATE("pending_update"),
    UNBOUND("unbound");

    @JsonValue
    private final String value;
}
