package com.ukhanov.realhelpdesk.core.security.captcha.utils;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CaptchaStorage {

    static long ttlMillis = java.util.concurrent.TimeUnit.MINUTES.toMillis(5);

    private static final Map<String, Entry> captchaMap = new ConcurrentHashMap<>();

    private record Entry(String code, long expiresAt) {
    }

    private CaptchaStorage() {
    }

    public static void put(String capId, String code) {
        purgeExpired();
        captchaMap.put(capId, new Entry(code, System.currentTimeMillis() + ttlMillis));
    }

    public static String get(String capId) {
        Entry entry = captchaMap.get(capId);
        if (entry == null) {
            return null;
        }
        if (isExpired(entry)) {
            captchaMap.remove(capId);
            return null;
        }
        return entry.code();
    }

    public static String remove(String capId) {
        Entry entry = captchaMap.remove(capId);
        return entry == null || isExpired(entry) ? null : entry.code();
    }

    private static boolean isExpired(Entry entry) {
        return System.currentTimeMillis() > entry.expiresAt();
    }

    private static void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Entry>> iterator = captchaMap.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now > iterator.next().getValue().expiresAt()) {
                iterator.remove();
            }
        }
    }
}
