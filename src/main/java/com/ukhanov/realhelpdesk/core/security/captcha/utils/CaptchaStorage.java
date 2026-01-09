package com.ukhanov.realhelpdesk.core.security.captcha.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CaptchaStorage {
    public static final Map<String, String> captchaMap = new ConcurrentHashMap<>();
}

