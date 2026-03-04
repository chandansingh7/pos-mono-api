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

    /**
     * Maximum number of hours a shift is allowed to remain open.
     * If {@code <= 0}, no maximum duration is enforced.
     *
     * Example property: {@code shift.max-open-hours=12}
     */
    @Value("${shift.max-open-hours:0}")
    private long maxOpenHours;

    /**
     * When true, disallow closing a shift that was opened on a previous
     * calendar day (local server time). Cashiers must ask an admin to
     * force-close such stale shifts.
     *
     * Example property: {@code shift.require-same-day=true}
     */
    @Value("${shift.require-same-day:false}")
    private boolean requireSameDay;

    public BigDecimal getMaxDifferenceAbsolute() {
        return maxDifferenceAbsolute;
    }

    public long getMinOpenMinutes() {
        return minOpenMinutes;
    }

    public long getMaxOpenHours() {
        return maxOpenHours;
    }

    public boolean isRequireSameDay() {
        return requireSameDay;
    }
}

