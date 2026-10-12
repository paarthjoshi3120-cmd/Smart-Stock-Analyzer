package trading;

/** Basic checks for simulated buy/sell orders. Call before changing database or portfolio state. */
public final class TradeValidator {
    private TradeValidator() {}

    public static void validateBuy(int quantity, int availableQuantity, double price) {
        common(quantity, price);
        if (availableQuantity < 0) throw new IllegalArgumentException("Available quantity cannot be negative");
        if (quantity > availableQuantity) throw new IllegalArgumentException("Not enough shares available");
    }

    public static void validateSell(int quantity, int ownedQuantity, double price) {
        common(quantity, price);
        if (ownedQuantity < 0) throw new IllegalArgumentException("Owned quantity cannot be negative");
        if (quantity > ownedQuantity) throw new IllegalArgumentException("Cannot sell more shares than owned");
    }

    private static void common(int quantity, double price) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than zero");
        if (Double.isNaN(price) || Double.isInfinite(price) || price <= 0)
            throw new IllegalArgumentException("Price must be finite and greater than zero");
    }

    public static void main(String[] args) {
        validateBuy(2, 10, 100.0);
        validateSell(1, 5, 100.0);
        System.out.println("Valid trade examples passed.");
        try {
            validateSell(6, 5, 100.0);
            throw new AssertionError("Expected invalid sell to be rejected");
        } catch (IllegalArgumentException expected) {
            System.out.println("Invalid sell correctly rejected: " + expected.getMessage());
        }
    }
}
