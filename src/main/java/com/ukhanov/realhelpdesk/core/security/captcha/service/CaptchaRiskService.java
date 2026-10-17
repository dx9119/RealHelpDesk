package com.ukhanov.realhelpdesk.core.security.captcha.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ukhanov.realhelpdesk.core.security.captcha.config.CaptchaProperties;

/**
 * Локальный риск-движок капчи: считает провалы проверок по IP и по подсети за скользящее окно. Внешние анти-бот провайдеры не используются
 * — только значения из конфигурации {@code captcha.*}.
 *
 * <p>
 * Два независимых сигнала дают разный отклик:
 * </p>
 * <ul>
 * <li><b>Провалы одного IP</b> ({@code captcha.risk-failure-threshold}) — мягкий риск {@link Risk#ELEVATED}: {@link CaptchaService}
 * повышает сложность PoW, но не выдаёт эмодзи-задание. Домашняя или NAT-сеть с одним внешним адресом на множество клиентов может набрать
 * такой порог из-за провалов отдельных пользователей, и мягкий отклик не наказывает всех.</li>
 * <li><b>Провалы разных IP одной подсети</b> ({@code captcha.risk-subnet-offender-threshold}, префиксы
 * {@code captcha.risk-subnet-prefix-v4|v6}) — признак распределённой атаки {@link Risk#EMOJI}: выдаётся эмодзи-задание. Один внешний адрес
 * NAT даёт в подсети один offender и этот порог не задевает.</li>
 * </ul>
 *
 * <p>
 * Оба счётчика живут в Caffeine с {@code expireAfterWrite} (окно {@code captcha.risk-failure-window-seconds}) и сбрасываются для IP после
 * успешной проверки ({@link #reset(String)}). Привязка к IP не жёсткая: адрес — только сигнал риска.
 * </p>
 */
@Service
public class CaptchaRiskService {

    /** Отклик на риск: обычная проверка, усложнённый PoW или эмодзи-эскалация. */
    public enum Risk {
        NONE, ELEVATED, EMOJI
    }

    private static final long MAX_TRACKED_IPS = 50_000;
    private static final long MAX_TRACKED_SUBNETS = 10_000;

    private final int failureThreshold;
    private final int subnetOffenderThreshold;
    private final int subnetPrefixV4;
    private final int subnetPrefixV6;

    private final Cache<String, Integer> failures;
    private final Cache<String, Set<String>> subnetOffenders;

    public CaptchaRiskService(CaptchaProperties properties) {
        Integer threshold = properties.getRiskFailureThreshold();
        Integer windowSeconds = properties.getRiskFailureWindowSeconds();
        Integer bonus = properties.getRiskElevatedDifficultyBonus();
        Integer offenderThreshold = properties.getRiskSubnetOffenderThreshold();
        Integer prefixV4 = properties.getRiskSubnetPrefixV4();
        Integer prefixV6 = properties.getRiskSubnetPrefixV6();
        if (threshold == null || threshold < 1 || threshold > 1_000) {
            throw new IllegalStateException("captcha.risk-failure-threshold должен быть в диапазоне 1..1000, получено " + threshold);
        }
        if (windowSeconds == null || windowSeconds < 10 || windowSeconds > 86_400) {
            throw new IllegalStateException(
                    "captcha.risk-failure-window-seconds должен быть в диапазоне 10..86400, получено " + windowSeconds);
        }
        if (bonus == null || bonus < 1 || bonus > 26) {
            throw new IllegalStateException("captcha.risk-elevated-difficulty-bonus должен быть в диапазоне 1..26, получено " + bonus);
        }
        if (offenderThreshold == null || offenderThreshold < 1 || offenderThreshold > 1_000) {
            throw new IllegalStateException(
                    "captcha.risk-subnet-offender-threshold должен быть в диапазоне 1..1000, получено " + offenderThreshold);
        }
        if (prefixV4 == null || prefixV4 < 1 || prefixV4 > 32) {
            throw new IllegalStateException("captcha.risk-subnet-prefix-v4 должен быть в диапазоне 1..32, получено " + prefixV4);
        }
        if (prefixV6 == null || prefixV6 < 1 || prefixV6 > 128) {
            throw new IllegalStateException("captcha.risk-subnet-prefix-v6 должен быть в диапазоне 1..128, получено " + prefixV6);
        }
        this.failureThreshold = threshold;
        this.subnetOffenderThreshold = offenderThreshold;
        this.subnetPrefixV4 = prefixV4;
        this.subnetPrefixV6 = prefixV6;
        this.failures = Caffeine.newBuilder().expireAfterWrite(windowSeconds, TimeUnit.SECONDS).maximumSize(MAX_TRACKED_IPS).build();
        this.subnetOffenders = Caffeine.newBuilder().expireAfterWrite(windowSeconds, TimeUnit.SECONDS).maximumSize(MAX_TRACKED_SUBNETS)
                .<String, Set<String>>build();
    }

    /** Учтён очередной провал капчи с этого IP: растёт счётчик IP и множество провалившихся IP его подсети. */
    public void recordFailure(String clientIp) {
        if (isBlank(clientIp)) {
            return;
        }
        failures.asMap().merge(clientIp, 1, Integer::sum);
        String subnet = subnetKey(clientIp);
        if (subnet != null) {
            subnetOffenders.asMap().computeIfAbsent(subnet, key -> ConcurrentHashMap.newKeySet()).add(clientIp);
        }
    }

    /** Уровень риска IP: эмодзи при распределённой атаке по подсети, усложнённый PoW при провалах одного IP. */
    public Risk riskLevel(String clientIp) {
        if (isBlank(clientIp)) {
            return Risk.NONE;
        }
        if (isSubnetDistributed(clientIp)) {
            return Risk.EMOJI;
        }
        Integer count = failures.getIfPresent(clientIp);
        return count != null && count >= failureThreshold ? Risk.ELEVATED : Risk.NONE;
    }

    /** Сброс сигналов IP после успешной проверки капчи. */
    public void reset(String clientIp) {
        if (isBlank(clientIp)) {
            return;
        }
        failures.invalidate(clientIp);
        String subnet = subnetKey(clientIp);
        if (subnet != null) {
            Set<String> offenders = subnetOffenders.getIfPresent(subnet);
            if (offenders != null) {
                offenders.remove(clientIp);
            }
        }
    }

    private boolean isSubnetDistributed(String clientIp) {
        String subnet = subnetKey(clientIp);
        if (subnet == null) {
            return false;
        }
        Set<String> offenders = subnetOffenders.getIfPresent(subnet);
        return offenders != null && offenders.size() >= subnetOffenderThreshold;
    }

    /**
     * Ключ подсети клиента: адрес с обнулёнными битами после префикса ({@code captcha.risk-subnet-prefix-v4|v6}) и длиной префикса.
     * {@code null} — адрес не разобран, сигнал подсети не учитывается.
     */
    private String subnetKey(String clientIp) {
        if (!looksLikeIp(clientIp)) {
            return null;
        }
        try {
            byte[] bytes = InetAddress.getByName(clientIp).getAddress();
            int prefix = bytes.length == 4 ? subnetPrefixV4 : subnetPrefixV6;
            int bits = Math.min(prefix, bytes.length * 8);
            byte[] masked = bytes.clone();
            for (int bit = bits; bit < bytes.length * 8; bit++) {
                masked[bit / 8] &= (byte) ~(1 << (7 - (bit % 8)));
            }
            return InetAddress.getByAddress(masked).getHostAddress() + "/" + bits;
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /** Числовой IPv4 или IPv6 с двоеточием: имена хостов не разбираем, чтобы не запускать DNS. */
    private static boolean looksLikeIp(String value) {
        if (value.indexOf(':') >= 0) {
            return true;
        }
        return value.matches("[0-9.]+");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
