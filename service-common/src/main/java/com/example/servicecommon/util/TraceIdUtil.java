package com.example.servicecommon.util;

import java.util.UUID;

/**
 * Created by shaosen on 2022/3/11
 */
public class TraceIdUtil {

    /**
     * generate traceId
     * @return
     */
    public static String getTraceId() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }
}
