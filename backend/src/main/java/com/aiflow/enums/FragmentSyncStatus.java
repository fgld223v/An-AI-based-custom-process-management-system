package com.aiflow.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FragmentSyncStatus implements DatabaseEnum {
    SYNCED("synced"),
    PENDING_UPDATE("pending_update"),
    UNBOUND("unbound");

    @JsonValue
    private final String value;
}
