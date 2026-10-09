package ssapp;

import java.time.LocalTime;
import java.util.*;

public class AppData {

    // ---------- DATA ----------

    static final List<User> USERS = new ArrayList<>();
    static final List<Company> COMPANIES = new ArrayList<>();
    static final Map<String, List<Holding>> PORTFOLIOS = new HashMap<>();
    static final List<String[]> HISTORY = new ArrayList<>();

    // ---------- INITIAL DATA ----------

    static {

        USERS.add(new User(
                "demo", "demo123",
                "Demo Investor", "USER", 100000
        ));

        USERS.add(new User(
                "Owner", "owner",
                "Administrator", "ADMIN", 1000000
        ));

        // ---------- INDIAN COMPANIES ----------

        COMPANIES.add(new Company(
                "RELIANCE", "Reliance Industries",
                "Energy", 2450.50, 1.85, "IN"
        ));

        COMPANIES.add(new Company(
                "TCS", "Tata Consultancy",
                "IT", 3780.25, 0.62, "IN"
        ));

        COMPANIES.add(new Company(
                "INFY", "Infosys",
                "IT", 1520.00, -0.45, "IN"
        ));

        COMPANIES.add(new Company(
                "HDFCBANK", "HDFC Bank",
                "Banking", 1685.75, 1.20, "IN"
        ));

        COMPANIES.add(new Company(
                "WIPRO", "Wipro",
                "IT", 430.10, -1.10, "IN"
        ));

        COMPANIES.add(new Company(
                "ICICIBANK", "ICICI Bank",
                "Banking", 1120.40, 0.95, "IN"
        ));

        COMPANIES.add(new Company(
                "BHARTIARTL", "Bharti Airtel",
                "Telecom", 890.00, 2.40, "IN"
        ));

        COMPANIES.add(new Company(
                "LT", "Larsen & Toubro",
                "Infra", 3260.90, 0.30, "IN"
        ));

        COMPANIES.add(new Company(
                "SBIN", "State Bank of India",
                "Banking", 780.35, -0.55, "IN"
        ));

        COMPANIES.add(new Company(
                "ASIANPAINT", "Asian Paints",
                "FMCG", 3140.60, 1.05, "IN"
        ));

        COMPANIES.add(new Company(
                "TATAMOTORS", "Tata Motors",
                "Auto", 985.40, 2.10, "IN"
        ));

        COMPANIES.add(new Company(
                "MARUTI", "Maruti Suzuki",
                "Auto", 12450.30, -0.85, "IN"
        ));

        // ---------- US COMPANIES ----------

        COMPANIES.add(new Company(
                "AAPL", "Apple Inc.",
                "Technology", 178.45, 0.85, "US"
        ));

        COMPANIES.add(new Company(
                "MSFT", "Microsoft Corp.",
                "Technology", 378.20, 1.10, "US"
        ));

        COMPANIES.add(new Company(
                "GOOGL", "Alphabet Inc.",
                "Technology", 142.60, 0.45, "US"
        ));

        COMPANIES.add(new Company(
                "AMZN", "Amazon.com Inc.",
                "E-Commerce", 156.90, -0.30, "US"
        ));

        COMPANIES.add(new Company(
                "TSLA", "Tesla Inc.",
                "Automotive", 245.30, 2.85, "US"
        ));

        COMPANIES.add(new Company(
                "NVDA", "NVIDIA Corp.",
                "Semiconductors", 495.20, 3.15, "US"
        ));

        // ---------- UK COMPANIES ----------

        COMPANIES.add(new Company(
                "HSBC", "HSBC Holdings",
                "Banking", 645.30, 0.55, "UK"
        ));

        COMPANIES.add(new Company(
                "BP", "BP plc",
                "Energy", 485.75, -1.20, "UK"
        ));

        COMPANIES.add(new Company(
                "AZN", "AstraZeneca",
                "Pharma", 10450.00, 0.90, "UK"
        ));

        // ---------- JAPAN COMPANIES ----------

        COMPANIES.add(new Company(
                "SONY", "Sony Group",
                "Electronics", 14250.00, 1.35, "JP"
        ));

        COMPANIES.add(new Company(
                "TM", "Toyota Motor",
                "Automotive", 2850.00, -0.40, "JP"
        ));

        // ---------- GERMANY COMPANIES ----------

        COMPANIES.add(new Company(
                "SAP", "SAP SE",
                "Software", 172.80, 0.70, "DE"
        ));

        COMPANIES.add(new Company(
                "BMW", "BMW AG",
                "Automotive", 98.45, 1.55, "DE"
        ));

        // ---------- CHINA COMPANIES ----------

        COMPANIES.add(new Company(
                "BABA", "Alibaba Group",
                "E-Commerce", 78.30, -0.95, "CN"
        ));

        COMPANIES.add(new Company(
                "JD", "JD.com",
                "E-Commerce", 28.75, 0.60, "CN"
        ));
    }

    // ---------- MODELS ----------

    static class User {

        String username;
        String password;
        String displayName;
        String role;
        double cash;

        User(
                String u,
                String p,
                String d,
                String r,
                double c
        ) {
            username = u;
            password = p;
            displayName = d;
            role = r;
            cash = c;
        }
    }

    static class Company {

        String symbol;
        String name;
        String sector;
        String country;

        double price;
        double changePct;

        Company(
                String s,
                String n,
                String sec,
                double p,
                double c,
                String co
        ) {
            symbol = s;
            name = n;
            sector = sec;
            price = p;
            changePct = c;
            country = co;
        }
    }

    static class Holding {

        String symbol;
        int qty;
        double avgPrice;

        Holding(
                String s,
                int q,
                double a
        ) {
            symbol = s;
            qty = q;
            avgPrice = a;
        }
    }

    // ---------- HELPER METHODS ----------

    static Company findCompany(String s) {

        for (Company c : COMPANIES) {

            if (c.symbol.equals(s)) {
                return c;
            }
        }

        return null;
    }

    static List<Holding> portfolioOf(String u) {

        return PORTFOLIOS.computeIfAbsent(
                u,
                k -> new ArrayList<>()
        );
    }

    static Holding findHolding(
            List<Holding> l,
            String s
    ) {

        for (Holding h : l) {

            if (h.symbol.equals(s)) {
                return h;
            }
        }

        return null;
    }

    static double invested(List<Holding> l) {

        double t = 0;

        for (Holding h : l) {
            t += h.qty * h.avgPrice;
        }

        return t;
    }

    static double totalValue(List<Holding> l) {

        double t = 0;

        for (Holding h : l) {

            Company c = findCompany(h.symbol);

            t += h.qty *
                    (c != null ? c.price : h.avgPrice);
        }

        return t;
    }

    static String inr(double v) {

        return "Rs." + String.format(
                "%,.2f",
                v
        );
    }

    static String pct(double v) {

        return String.format(
                "%+.2f%%",
                v
        );
    }

    static String now() {

        return LocalTime.now()
                .withNano(0)
                .toString();
    }

    static String countryFlag(String c) {

        switch (c) {

            case "IN":
                return "🇮🇳 India";

            case "US":
                return "🇺🇸 USA";

            case "UK":
                return "🇬🇧 UK";

            case "JP":
                return "🇯🇵 Japan";

            case "DE":
                return "🇩🇪 Germany";

            case "CN":
                return "🇨🇳 China";

            default:
                return c;
        }
    }
}
