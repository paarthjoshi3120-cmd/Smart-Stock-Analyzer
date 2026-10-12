package risk;

/**
 * Dependency-free boundary test based on the thresholds in the project report.
 * Confirm the exact inclusivity matches your current risk engine before integrating.
 */
public class RiskThresholdTest {
    enum Band { ALLOW, REMINDER, STRONG_WARNING, RESTRICTION }

    static Band classify(double score) {
        if (Double.isNaN(score) || score < 0 || score > 1)
            throw new IllegalArgumentException("Risk score must be between 0 and 1");
        if (score < 0.40) return Band.ALLOW;
        if (score <= 0.70) return Band.REMINDER;
        if (score <= 0.85) return Band.STRONG_WARNING;
        return Band.RESTRICTION;
    }

    private static void check(double score, Band expected) {
        Band actual = classify(score);
        if (actual != expected)
            throw new AssertionError("score " + score + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        check(0.39, Band.ALLOW);
        check(0.40, Band.REMINDER);
        check(0.69, Band.REMINDER);
        check(0.70, Band.REMINDER);
        check(0.71, Band.STRONG_WARNING);
        check(0.85, Band.STRONG_WARNING);
        check(0.86, Band.RESTRICTION);
        System.out.println("All risk threshold boundary checks passed.");
    }
}
