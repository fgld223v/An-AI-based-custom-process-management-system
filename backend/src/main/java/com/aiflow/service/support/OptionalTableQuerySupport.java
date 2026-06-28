package com.aiflow.service.support;

import java.util.Locale;

/**
 * 可选表查询支持工具，用于判断异常是否由目标数据表缺失引起，便于优雅降级。
 */
public final class OptionalTableQuerySupport {

    private OptionalTableQuerySupport() {
    }

    public static boolean isMissingOptionalTable(Throwable throwable, String tableName) {
        String normalizedTableName = tableName.toLowerCase(Locale.ROOT);
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message == null) {
                continue;
            }
            String normalizedMessage = message.toLowerCase(Locale.ROOT);
            if (normalizedMessage.contains(normalizedTableName)
                    && (normalizedMessage.contains("doesn't exist")
                    || normalizedMessage.contains("does not exist")
                    || normalizedMessage.contains("unknown table")
                    || normalizedMessage.contains("not found")
                    || normalizedMessage.contains("table or view not found"))) {
                return true;
            }
        }
        return false;
    }
}
