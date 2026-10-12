package portfolio;

/** Reusable helpers for simulated portfolio calculations. Merge with existing code if equivalent methods exist. */
public final class PortfolioCalculator {
    private PortfolioCalculator() {}

    public static double profitLoss(int quantity, double averageCost, double currentPrice) {
        nonNegative(quantity, "quantity");
        nonNegative(averageCost, "averageCost");
        nonNegative(currentPrice, "currentPrice");
        return (currentPrice - averageCost) * quantity;
    }

    public static double returnPercent(double averageCost, double currentPrice) {
        nonNegative(averageCost, "averageCost");
        nonNegative(currentPrice, "currentPrice");
        if (averageCost == 0.0) throw new IllegalArgumentException("averageCost must be greater than zero");
        return ((currentPrice - averageCost) / averageCost) * 100.0;
    }

    public static double weightedAverageCost(int oldQty, double oldAvg, int newQty, double newPrice) {
        nonNegative(oldQty, "oldQty");
        nonNegative(newQty, "newQty");
        nonNegative(oldAvg, "oldAvg");
        nonNegative(newPrice, "newPrice");
        int total = oldQty + newQty;
        if (total == 0) throw new IllegalArgumentException("total quantity must be greater than zero");
        return ((oldQty * oldAvg) + (newQty * newPrice)) / total;
    }

    public static double breakEvenPrice(double averageCost, double totalCharges, int quantity) {
        nonNegative(averageCost, "averageCost");
        nonNegative(totalCharges, "totalCharges");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be greater than zero");
        return averageCost + totalCharges / quantity;
    }

    private static void nonNegative(double value, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0)
            throw new IllegalArgumentException(field + " must be finite and non-negative");
    }
    private static void nonNegative(int value, String field) {
        if (value < 0) throw new IllegalArgumentException(field + " must be non-negative");
    }

    public static void main(String[] args) {
        System.out.println("P/L: " + profitLoss(10, 100.0, 112.0));
        System.out.println("Return: " + returnPercent(100.0, 112.0) + "%");
        System.out.println("Average cost: " + weightedAverageCost(10, 100.0, 5, 110.0));
        System.out.println("Break-even: " + breakEvenPrice(100.0, 20.0, 10));
    }
}
