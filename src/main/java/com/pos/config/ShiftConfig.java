package com.pos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Configuration for cashier shift behaviour (tolerances, time rules, etc.).
 *
 * All properties are optional and can be tuned per deployment via
 * application.properties / environment variables.
 */
@Component
public class ShiftConfig {

    /**
     * Maximum absolute cash difference (over/short) allowed when closing a shift.
     * If {@code <= 0}, no restriction is enforced.
     *
     * Example property: {@code shift.max-difference-absolute=5.00}
     */
    @Value("${shift.max-difference-absolute:0}")
    private BigDecimal maxDifferenceAbsolute;

    /**
     * Minimum number of minutes a shift must be open before it can be closed.
     * If {@code <= 0}, no minimum duration is enforced.
     *
     * Example property: {@code shift.min-open-minutes=30}
     */
    @Value("${shift.min-open-minutes:0}")
    private long minOpenMinutes;

    public BigDecimal getMaxDifferenceAbsolute() {
        return maxDifferenceAbsolute;
    }

    public long getMinOpenMinutes() {
        return minOpenMinutes;
    }
}

