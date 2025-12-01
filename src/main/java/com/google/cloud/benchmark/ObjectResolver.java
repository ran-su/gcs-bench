package com.google.cloud.benchmark;

public class ObjectResolver {
    public static String resolveName(String format, String objectName, int threadId, int objectId) {
        if (format == null || format.isEmpty()) {
            return objectName;
        }
        return format.replace("{t}", String.valueOf(threadId))
                     .replace("{o}", String.valueOf(objectId));
    }
}
