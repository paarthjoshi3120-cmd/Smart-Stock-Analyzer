import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.net.URI;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class SSAApp {

    // ---------- THEME ----------
    static final Color BG_DEEP    = new Color(13, 16, 24);
    static final Color BG_PANEL   = new Color(20, 24, 34);
    static final Color BG_CARD    = new Color(28, 33, 46);
    static final Color ROW_ALT    = new Color(32, 38, 53);
    static final Color BG_INPUT   = new Color(38, 44, 60);
    static final Color BORDER     = new Color(52, 60, 82);
    static final Color TEXT       = new Color(240, 244, 252);
    static final Color TEXT_MUTED = new Color(150, 160, 185);
    static final Color ACCENT     = new Color(99, 155, 255);
    static final Color SUCCESS    = new Color(76, 217, 145);
    static final Color DANGER     = new Color(255, 96, 110);
    static final Color WARNING    = new Color(255, 195, 90);

    static final Font H1    = new Font("SansSerif", Font.BOLD, 22);
    static final Font H2    = new Font("SansSerif", Font.BOLD, 16);
    static final Font H3    = new Font("SansSerif", Font.BOLD, 13);
    static final Font BODY  = new Font("SansSerif", Font.PLAIN, 13);
    static final Font SMALL = new Font("SansSerif", Font.PLAIN, 11);
    static final Font SMALL_B = new Font("SansSerif", Font.BOLD, 11);

    // ---------- DATA ----------
    static final List<Company> COMPANIES = new ArrayList<>();
    static final Map<String, List<Holding>> PORTFOLIOS = new HashMap<>();
    static final List<String[]> HISTORY = new ArrayList<>();
    static final Map<String, List<Candle>> PRICE_HISTORY = new HashMap<>();

    // ---------- BACKEND CONNECTION (Java Swing -> Flask REST API) ----------
    static final ApiClient API = new ApiClient("http://127.0.0.1:5000");

    static class ApiException extends Exception {
        final int statusCode;
        final Map<String, Object> payload;
        ApiException(int statusCode, Map<String, Object> payload) {
            super(String.valueOf(payload.getOrDefault("message", "Backend request failed")));
            this.statusCode = statusCode;
            this.payload = payload;
        }
    }

    static class ApiClient {
        private final String baseUrl;
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        private String csrfToken = "";

        ApiClient(String baseUrl) { this.baseUrl = baseUrl; }

        synchronized Map<String, Object> get(String path) throws Exception {
            return send("GET", path, null, false);
        }
        synchronized Map<String, Object> post(String path, Map<String, Object> body) throws Exception {
            return send("POST", path, body, true);
        }
        synchronized Map<String, Object> delete(String path) throws Exception {
            return send("DELETE", path, null, true);
        }
        synchronized Map<String, Object> login(String email, String password) throws Exception {
            Map<String, Object> result = send("POST", "/api/login", mapOf("email", email, "password", password), false);
            csrfToken = stringValue(result.get("csrf_token"));
            return result;
        }
        synchronized void clearSession() { csrfToken = ""; cookies.getCookieStore().removeAll(); }

        private Map<String, Object> send(String method, String path, Map<String, Object> body, boolean csrf) throws Exception {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(20)).header("Accept", "application/json");
            if (body != null) {
                b.header("Content-Type", "application/json; charset=utf-8")
                 .method(method, HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8));
            } else if (method.equals("GET")) {
                b.GET();
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            if (csrf && !csrfToken.isEmpty()) b.header("X-CSRF-Token", csrfToken);
            HttpResponse<String> response = client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Map<String, Object> parsed;
            try { parsed = asMap(new JsonParser(response.body()).parse()); }
            catch (Exception ex) { parsed = new LinkedHashMap<>(); parsed.put("message", response.body()); }
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new ApiException(response.statusCode(), parsed);
            return parsed;
        }

        private static String toJson(Object value) {
            if (value == null) return "null";
            if (value instanceof String) return "\"" + ((String)value).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
            if (value instanceof Number || value instanceof Boolean) return value.toString();
            if (value instanceof Map) {
                StringBuilder out = new StringBuilder("{"); boolean first = true;
                for (Object entryObj : ((Map<?, ?>)value).entrySet()) {
                    Map.Entry<?, ?> e = (Map.Entry<?, ?>)entryObj;
                    if (!first) out.append(','); first = false;
                    out.append(toJson(String.valueOf(e.getKey()))).append(':').append(toJson(e.getValue()));
                }
                return out.append('}').toString();
            }
            if (value instanceof Iterable) {
                StringBuilder out = new StringBuilder("["); boolean first = true;
                for (Object item : (Iterable<?>)value) { if (!first) out.append(','); first = false; out.append(toJson(item)); }
                return out.append(']').toString();
            }
            return toJson(value.toString());
        }
    }

    static Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) m.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return m;
    }
    @SuppressWarnings("unchecked") static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>)o : new LinkedHashMap<>();
    }
    @SuppressWarnings("unchecked") static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>)o : new ArrayList<>();
    }
    static String stringValue(Object o) { return o == null ? "" : String.valueOf(o); }
    static double doubleValue(Object o) { try { return o instanceof Number ? ((Number)o).doubleValue() : Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; } }
    static long longValue(Object o) { try { return o instanceof Number ? ((Number)o).longValue() : Long.parseLong(String.valueOf(o)); } catch (Exception e) { return 0; } }

    // Small JSON parser so the Swing frontend needs no third-party JSON library.
    static class JsonParser {
        final String text; int pos = 0;
        JsonParser(String text) { this.text = text == null ? "" : text; }
        Object parse() { skip(); Object v = value(); skip(); return v; }
        void skip() { while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++; }
        Object value() {
            skip(); if (pos >= text.length()) return null;
            char c = text.charAt(pos);
            if (c == '{') return object(); if (c == '[') return array(); if (c == '"') return string();
            if (c == 't' && take("true")) return Boolean.TRUE;
            if (c == 'f' && take("false")) return Boolean.FALSE;
            if (c == 'n' && take("null")) return null;
            return number();
        }
        boolean take(String s) { if (text.startsWith(s, pos)) { pos += s.length(); return true; } return false; }
        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>(); pos++; skip();
            if (pos < text.length() && text.charAt(pos) == '}') { pos++; return m; }
            while (pos < text.length()) {
                skip(); String k = string(); skip(); if (pos < text.length() && text.charAt(pos) == ':') pos++;
                m.put(k, value()); skip(); if (pos < text.length() && text.charAt(pos) == ',') { pos++; continue; }
                if (pos < text.length() && text.charAt(pos) == '}') pos++; break;
            }
            return m;
        }
        List<Object> array() {
            List<Object> a = new ArrayList<>(); pos++; skip();
            if (pos < text.length() && text.charAt(pos) == ']') { pos++; return a; }
            while (pos < text.length()) { a.add(value()); skip(); if (pos < text.length() && text.charAt(pos) == ',') { pos++; continue; } if (pos < text.length() && text.charAt(pos) == ']') pos++; break; }
            return a;
        }
        String string() {
            StringBuilder b = new StringBuilder(); if (pos < text.length() && text.charAt(pos) == '"') pos++;
            while (pos < text.length()) {
                char c = text.charAt(pos++); if (c == '"') break;
                if (c == '\\' && pos < text.length()) {
                    char e = text.charAt(pos++);
                    switch (e) { case '"': b.append('"'); break; case '\\': b.append('\\'); break; case '/': b.append('/'); break; case 'b': b.append('\b'); break; case 'f': b.append('\f'); break; case 'n': b.append('\n'); break; case 'r': b.append('\r'); break; case 't': b.append('\t'); break; case 'u': if (pos + 4 <= text.length()) { b.append((char)Integer.parseInt(text.substring(pos, pos + 4), 16)); pos += 4; } break; default: b.append(e); }
                } else b.append(c);
            }
            return b.toString();
        }
        Number number() {
            int start = pos; while (pos < text.length() && "-+0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
            String n = text.substring(start, pos); try { if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.parseDouble(n); return Long.parseLong(n); } catch (Exception e) { return 0; }
        }
    }

    static void loadBackendData(User user) throws Exception {
        Map<String, Object> stockResponse = API.get("/api/stocks");
        Map<String, Company> existingCompanies = new HashMap<>();
        for (Company c : COMPANIES) existingCompanies.put(c.symbol, c);
        COMPANIES.clear();
        for (Object item : asList(stockResponse.get("data"))) {
            Map<String, Object> r = asMap(item);
            String symbol = stringValue(r.get("symbol"));
            Company c = existingCompanies.get(symbol);
            if (c == null) c = new Company(symbol, stringValue(r.get("name")),
                    stringValue(r.get("sector")), doubleValue(r.get("price")),
                    doubleValue(r.get("changePct")), stringValue(r.get("country")));
            c.name = stringValue(r.get("name")); c.sector = stringValue(r.get("sector"));
            c.price = doubleValue(r.get("price")); c.changePct = doubleValue(r.get("changePct"));
            c.country = stringValue(r.get("country"));
            List<Candle> cachedHistory = PRICE_HISTORY.get(symbol);
            if (cachedHistory != null && !cachedHistory.isEmpty()) {
                Candle last = cachedHistory.get(cachedHistory.size() - 1);
                last.c = c.price; last.h = Math.max(last.h, c.price); last.l = Math.min(last.l, c.price);
            }
            COMPANIES.add(c);
        }
        Map<String, Object> portfolio = API.get("/api/portfolio");
        user.cash = doubleValue(portfolio.get("cash"));
        List<Holding> holdings = portfolioOf(user.username); holdings.clear();
        for (Object item : asList(portfolio.get("holdings"))) {
            Map<String, Object> r = asMap(item);
            Holding h = new Holding(stringValue(r.get("symbol")), (int)longValue(r.get("quantity")), doubleValue(r.get("avg_price")));
            h.backendProfitLoss = doubleValue(r.get("profit_loss"));
            holdings.add(h);
        }
        HISTORY.clear();
        Map<String, Object> history = API.get("/api/history");
        for (Object item : asList(history.get("data"))) {
            Map<String, Object> r = asMap(item);
            String type = stringValue(r.get("type"));
            double gross = doubleValue(r.get("price")) * longValue(r.get("quantity"));
            double fee = doubleValue(r.get("fee"));
            double total = "BUY".equals(type) ? gross + fee : gross - fee;
            HISTORY.add(new String[]{stringValue(r.get("ts")), type, stringValue(r.get("symbol")),
                    stringValue(r.get("quantity")), inr(doubleValue(r.get("price"))), inr(total), user.username});
        }
        Collections.reverse(HISTORY); // UI history tables expect oldest-to-newest storage.
    }

    static {
        // Indian
        COMPANIES.add(new Company("RELIANCE", "Reliance Industries", "Energy", 2450.50, 1.85, "IN"));
        COMPANIES.add(new Company("TCS", "Tata Consultancy", "IT", 3780.25, 0.62, "IN"));
        COMPANIES.add(new Company("INFY", "Infosys", "IT", 1520.00, -0.45, "IN"));
        COMPANIES.add(new Company("HDFCBANK", "HDFC Bank", "Banking", 1685.75, 1.20, "IN"));
        COMPANIES.add(new Company("WIPRO", "Wipro", "IT", 430.10, -1.10, "IN"));
        COMPANIES.add(new Company("ICICIBANK", "ICICI Bank", "Banking", 1120.40, 0.95, "IN"));
        COMPANIES.add(new Company("BHARTIARTL", "Bharti Airtel", "Telecom", 890.00, 2.40, "IN"));
        COMPANIES.add(new Company("LT", "Larsen & Toubro", "Infra", 3260.90, 0.30, "IN"));
        COMPANIES.add(new Company("SBIN", "State Bank of India", "Banking", 780.35, -0.55, "IN"));
        COMPANIES.add(new Company("ASIANPAINT", "Asian Paints", "FMCG", 3140.60, 1.05, "IN"));
        COMPANIES.add(new Company("TATAMOTORS", "Tata Motors", "Auto", 985.40, 2.10, "IN"));
        COMPANIES.add(new Company("MARUTI", "Maruti Suzuki", "Auto", 12450.30, -0.85, "IN"));

        // US
        COMPANIES.add(new Company("AAPL", "Apple Inc.", "Technology", 178.45, 0.85, "US"));
        COMPANIES.add(new Company("MSFT", "Microsoft Corp.", "Technology", 378.20, 1.10, "US"));
        COMPANIES.add(new Company("GOOGL", "Alphabet Inc.", "Technology", 142.60, 0.45, "US"));
        COMPANIES.add(new Company("AMZN", "Amazon.com Inc.", "E-Commerce", 156.90, -0.30, "US"));
        COMPANIES.add(new Company("TSLA", "Tesla Inc.", "Automotive", 245.30, 2.85, "US"));
        COMPANIES.add(new Company("NVDA", "NVIDIA Corp.", "Semiconductors", 495.20, 3.15, "US"));

        // UK
        COMPANIES.add(new Company("HSBC", "HSBC Holdings", "Banking", 645.30, 0.55, "UK"));
        COMPANIES.add(new Company("BP", "BP plc", "Energy", 485.75, -1.20, "UK"));
        COMPANIES.add(new Company("AZN", "AstraZeneca", "Pharma", 10450.00, 0.90, "UK"));

        // Japan
        COMPANIES.add(new Company("SONY", "Sony Group", "Electronics", 14250.00, 1.35, "JP"));
        COMPANIES.add(new Company("TM", "Toyota Motor", "Automotive", 2850.00, -0.40, "JP"));

        // Germany
        COMPANIES.add(new Company("SAP", "SAP SE", "Software", 172.80, 0.70, "DE"));
        COMPANIES.add(new Company("BMW", "BMW AG", "Automotive", 98.45, 1.55, "DE"));

        // China
        COMPANIES.add(new Company("BABA", "Alibaba Group", "E-Commerce", 78.30, -0.95, "CN"));
        COMPANIES.add(new Company("JD", "JD.com", "E-Commerce", 28.75, 0.60, "CN"));
    }

    // ---------- MODELS ----------
    static class User {
        long userId;
        String username, password, displayName, role;
        double cash;
        User(String u, String p, String d, String r, double c) { this(0, u, p, d, r, c); }
        User(long id, String u, String p, String d, String r, double c) {
            userId = id; username = u; password = p; displayName = d; role = r; cash = c;
        }
    }
    static class Company {
        String symbol, name, sector, country;
        double price, changePct;
        Company(String s, String n, String sec, double p, double c, String co) {
            symbol = s; name = n; sector = sec; price = p; changePct = c; country = co;
        }
    }
    static class Holding {
        String symbol; int qty; double avgPrice; double backendProfitLoss = Double.NaN;
        Holding(String s, int q, double a) { symbol = s; qty = q; avgPrice = a; }
    }
    static class Candle {
        LocalDate date; double o, h, l, c; long v;
    }

    static Company findCompany(String s) {
        for (Company c : COMPANIES) if (c.symbol.equals(s)) return c;
        return null;
    }
    static List<Holding> portfolioOf(String u) {
        return PORTFOLIOS.computeIfAbsent(u, k -> new ArrayList<>());
    }
    static Holding findHolding(List<Holding> l, String s) {
        for (Holding h : l) if (h.symbol.equals(s)) return h;
        return null;
    }
    static double invested(List<Holding> l) {
        double t = 0; for (Holding h : l) t += h.qty * h.avgPrice; return t;
    }
    static double totalValue(List<Holding> l) {
        double t = 0;
        for (Holding h : l) {
            Company c = findCompany(h.symbol);
            t += h.qty * (c != null ? c.price : h.avgPrice);
        }
        return t;
    }
    static String inr(double v) { return "Rs." + String.format("%,.2f", v); }
    static String signedInr(double v) { return (v >= 0 ? "+" : "-") + "Rs." + String.format("%,.2f", Math.abs(v)); }
    static String pct(double v) { return String.format("%+.2f%%", v); }
    static String now() { return LocalTime.now().withNano(0).toString(); }
    static String num(double v) { return String.format("%,.2f", v); }
    static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1e9) return String.format("%.2fB", v / 1e9);
        if (a >= 1e6) return String.format("%.2fM", v / 1e6);
        if (a >= 1e3) return String.format("%.1fK", v / 1e3);
        return String.format("%.0f", v);
    }
    static String hex(Color c) { return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()); }

    // ---------- SIMULATED PRICE HISTORY (deterministic per symbol) ----------
    static List<Candle> history(Company c) {
        return PRICE_HISTORY.computeIfAbsent(c.symbol, k -> generateHistory(c));
    }

    static List<Candle> generateHistory(Company comp) {
        final int n = 260;
        Random rnd = new Random(comp.symbol.hashCode() * 31L + 7);
        double vol;
        switch (comp.sector) {
            case "Technology": case "Semiconductors": case "E-Commerce": case "Automotive": vol = 0.021; break;
            case "Energy": case "Auto": case "Electronics": vol = 0.017; break;
            case "Banking": case "Telecom": case "Infra": vol = 0.013; break;
            default: vol = 0.011;
        }
        double[] close = new double[n];
        close[n - 1] = comp.price;
        close[n - 2] = comp.price / (1 + comp.changePct / 100.0);
        for (int i = n - 2; i >= 1; i--) {
            double r = rnd.nextGaussian() * vol + 0.0005;
            close[i - 1] = Math.max(0.5, close[i] / (1 + r));
        }
        // business-day calendar ending on the latest weekday
        LocalDate[] dates = new LocalDate[n];
        LocalDate d = LocalDate.now();
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.minusDays(1);
        for (int i = n - 1; i >= 0; i--) {
            dates[i] = d;
            d = d.minusDays(1);
            while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.minusDays(1);
        }
        long baseVol = 500_000 + rnd.nextInt(4_000_000);
        List<Candle> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Candle k = new Candle();
            k.date = dates[i];
            k.c = close[i];
            k.o = i == 0 ? close[0] * (1 + rnd.nextGaussian() * 0.004) : close[i - 1];
            k.h = Math.max(k.o, k.c) * (1 + Math.abs(rnd.nextGaussian()) * vol * 0.5);
            k.l = Math.min(k.o, k.c) * (1 - Math.abs(rnd.nextGaussian()) * vol * 0.5);
            double move = Math.abs(k.c / k.o - 1);
            k.v = (long) (baseVol * (0.6 + rnd.nextDouble() * 0.8) * (1 + move * 25));
            out.add(k);
        }
        return out;
    }

    // ---------- WIDGETS ----------
    static class RoundButton extends JButton {
        final Color base;
        RoundButton(String text, Color base) {
            super(text);
            this.base = base;
            setFont(H3);
            setForeground(Color.WHITE);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(9, 18, 9, 18));
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = base;
            if (!isEnabled()) c = BG_INPUT;
            else if (getModel().isPressed()) c = base.darker();
            else if (getModel().isRollover()) c = base.brighter();
            g.setColor(c);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g.dispose();
            super.paintComponent(g0);
        }
    }

    static class Pill extends JToggleButton {
        Pill(String text) {
            super(text);
            setFont(SMALL_B);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(6, 13, 6, 13));
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isSelected()) {
                g.setColor(ACCENT);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                setForeground(Color.WHITE);
            } else {
                if (getModel().isRollover()) {
                    g.setColor(BG_INPUT);
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                }
                setForeground(TEXT_MUTED);
            }
            g.dispose();
            super.paintComponent(g0);
        }
    }

    static JButton btn(String text, Color bg) { return new RoundButton(text, bg); }

    static JTextField input() {
        JTextField f = new JTextField();
        f.setBackground(BG_INPUT); f.setForeground(TEXT);
        f.setCaretColor(TEXT); f.setFont(BODY);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(9, 12, 9, 12)));
        f.setPreferredSize(new Dimension(220, 36));
        return f;
    }
    static JPasswordField passInput() {
        JPasswordField f = new JPasswordField();
        f.setBackground(BG_INPUT); f.setForeground(TEXT);
        f.setCaretColor(TEXT); f.setFont(BODY);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(9, 12, 9, 12)));
        f.setPreferredSize(new Dimension(220, 36));
        return f;
    }
    @SuppressWarnings("unchecked")
    static void styleCombo(JComboBox<?> c) {
        c.setBackground(BG_INPUT); c.setForeground(TEXT); c.setFont(BODY);
        ((JComboBox<Object>) c).setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                     boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, value, index, sel, false);
                setBackground(sel ? new Color(45, 75, 140) : BG_INPUT);
                setForeground(TEXT);
                setBorder(new EmptyBorder(6, 10, 6, 10));
                return this;
            }
        });
    }
    static JLabel h1(String s) { JLabel l = new JLabel(s); l.setFont(H1); l.setForeground(TEXT); return l; }
    static JLabel h2(String s) { JLabel l = new JLabel(s); l.setFont(H2); l.setForeground(TEXT); return l; }
    static JLabel muted(String s) { JLabel l = new JLabel(s); l.setFont(SMALL); l.setForeground(TEXT_MUTED); return l; }

    static void onChange(JTextField f, Runnable r) {
        f.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { r.run(); }
            public void removeUpdate(DocumentEvent e) { r.run(); }
            public void changedUpdate(DocumentEvent e) { r.run(); }
        });
    }

    // Table cell renderer: padding, zebra rows, optional green/red colouring
    static class CellR extends DefaultTableCellRenderer {
        final int align; final boolean colorize;
        CellR(int align, boolean colorize) { this.align = align; this.colorize = colorize; }
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                  boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, false, r, c);
            setHorizontalAlignment(align);
            setBorder(new EmptyBorder(0, 10, 0, 10));
            setFont(BODY);
            String s = v == null ? "" : v.toString();
            if (sel) {
                setBackground(new Color(45, 75, 140));
                setForeground(Color.WHITE);
            } else {
                setBackground(r % 2 == 0 ? BG_CARD : ROW_ALT);
                setForeground(colorize ? (s.startsWith("+") ? SUCCESS : s.startsWith("-") ? DANGER : TEXT) : TEXT);
            }
            return this;
        }
    }

    static JTable darkTable(DefaultTableModel m) {
        JTable t = new JTable(m);
        t.setBackground(BG_CARD); t.setForeground(TEXT);
        t.setRowHeight(34); t.setFont(BODY);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setSelectionBackground(new Color(45, 75, 140));
        t.setSelectionForeground(Color.WHITE);
        t.setFillsViewportHeight(true);
        t.setDefaultRenderer(Object.class, new CellR(SwingConstants.LEFT, false));
        JTableHeaderStyle(t);
        return t;
    }

    static void JTableHeaderStyle(JTable t) {
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setPreferredSize(new Dimension(0, 36));
        t.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean sel,
                                                                      boolean foc, int r, int c) {
                super.getTableCellRendererComponent(tb, v == null ? "" : v.toString().toUpperCase(), false, false, r, c);
                setOpaque(true);
                setBackground(BG_PANEL);
                setForeground(TEXT_MUTED);
                setFont(SMALL_B);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                        new EmptyBorder(0, 10, 0, 10)));
                javax.swing.table.TableCellRenderer cr = tb.getColumnModel().getColumn(c).getCellRenderer();
                setHorizontalAlignment(cr instanceof CellR ? ((CellR) cr).align : SwingConstants.LEFT);
                return this;
            }
        });
    }

    static class DarkScrollUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor = new Color(70, 80, 108);
            trackColor = BG_CARD;
        }
        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }
        JButton zero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setBorder(null);
            return b;
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(BG_CARD);
            g.fillRect(r.x, r.y, r.width, r.height);
        }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isThumbRollover() ? new Color(95, 108, 140) : thumbColor);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
            g2.dispose();
        }
    }

    static JScrollPane scroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        sp.getViewport().setBackground(BG_CARD);
        sp.getVerticalScrollBar().setUI(new DarkScrollUI());
        sp.getHorizontalScrollBar().setUI(new DarkScrollUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(11, 0));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 11));
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }
    static CellR rightR() { return new CellR(SwingConstants.RIGHT, false); }
    static CellR colorR() { return new CellR(SwingConstants.RIGHT, true); }

    static String countryFlag(String c) {
        switch (c) {
            case "IN": return "IN · India";
            case "US": return "US · USA";
            case "UK": return "GB · UK";
            case "JP": return "JP · Japan";
            case "DE": return "DE · Germany";
            case "CN": return "CN · China";
            default:   return c;
        }
    }

    // =====================================================================
    //                         STOCK CHART COMPONENT
    // =====================================================================
    static class StockChart extends JComponent {
        List<Candle> data = new ArrayList<>();
        boolean candle = false, showMA = true, valueOnly = false;
        int hover = -1, mouseY = 0;
        static final int PL = 12, PR = 76, PT = 16, PB = 28;
        double min, max;
        int chartBottom;

        static final DateTimeFormatter F_SHORT = DateTimeFormatter.ofPattern("dd MMM");
        static final DateTimeFormatter F_LONG  = DateTimeFormatter.ofPattern("MMM yy");
        static final DateTimeFormatter F_FULL  = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy");

        StockChart() {
            setOpaque(false);
            setPreferredSize(new Dimension(400, 300));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { track(e); }
                @Override public void mouseDragged(MouseEvent e) { track(e); }
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        }

        void track(MouseEvent e) {
            int n = data.size();
            if (n == 0) return;
            int w = getWidth() - PL - PR;
            double slot = (double) w / n;
            int i = (int) Math.floor((e.getX() - PL) / slot);
            hover = (e.getX() < PL || e.getX() > PL + w || i < 0 || i >= n) ? -1 : i;
            mouseY = e.getY();
            repaint();
        }

        void setData(List<Candle> d) { data = d; hover = -1; repaint(); }

        String axis(double v) { return valueOnly ? compact(v) : num(v); }

        double yOf(double v) { return PT + (max - v) / (max - min) * (chartBottom - PT); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int W = getWidth(), H = getHeight();
            int n = data.size();

            if (n < 2) {
                g.setFont(BODY);
                g.setColor(TEXT_MUTED);
                String s = "Select a company to view its chart";
                g.drawString(s, (W - g.getFontMetrics().stringWidth(s)) / 2, H / 2);
                g.dispose();
                return;
            }

            int plotW = W - PL - PR;
            double slot = (double) plotW / n;
            int volH = valueOnly ? 0 : (int) ((H - PT - PB) * 0.17);
            chartBottom = H - PB - volH - 10;

            min = Double.MAX_VALUE; max = -Double.MAX_VALUE;
            for (Candle c : data) {
                min = Math.min(min, candle ? c.l : c.c);
                max = Math.max(max, candle ? c.h : c.c);
            }
            double pad = (max - min) * 0.08;
            if (pad == 0) pad = max * 0.01;
            min -= pad; max += pad;

            Candle first = data.get(0), last = data.get(n - 1);
            Color trend = last.c >= first.c ? SUCCESS : DANGER;

            // grid + y labels
            g.setFont(SMALL);
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i <= 4; i++) {
                double v = max - (max - min) * i / 4.0;
                int y = (int) Math.round(yOf(v));
                g.setColor(new Color(255, 255, 255, 16));
                g.setStroke(new BasicStroke(1f));
                g.drawLine(PL, y, W - PR, y);
                g.setColor(TEXT_MUTED);
                g.drawString(axis(v), W - PR + 8, y + fm.getAscent() / 2 - 1);
            }

            // x labels
            DateTimeFormatter df = n > 100 ? F_LONG : F_SHORT;
            int ticks = Math.min(6, n);
            for (int t = 0; t < ticks; t++) {
                int i = (int) Math.round((n - 1) * (t + 0.5) / ticks);
                String s = data.get(i).date.format(df);
                int lw = fm.stringWidth(s);
                double x = PL + (i + 0.5) * slot;
                int lx = (int) Math.max(PL, Math.min(W - PR - lw, x - lw / 2.0));
                g.setColor(TEXT_MUTED);
                g.drawString(s, lx, H - 8);
            }

            // volume bars
            long maxVol = 1;
            for (Candle c : data) maxVol = Math.max(maxVol, c.v);
            int bw = Math.max(1, (int) (slot * 0.7));
            for (int i = 0; i < n; i++) {
                Candle c = data.get(i);
                int bh = (int) ((double) c.v / maxVol * volH);
                Color col = c.c >= c.o ? SUCCESS : DANGER;
                g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 70));
                int x = (int) (PL + (i + 0.5) * slot - bw / 2.0);
                g.fillRect(x, H - PB - bh, bw, bh);
            }

            if (!candle) {
                // area + line
                Path2D line = new Path2D.Double();
                for (int i = 0; i < n; i++) {
                    double x = PL + (i + 0.5) * slot, y = yOf(data.get(i).c);
                    if (i == 0) line.moveTo(x, y); else line.lineTo(x, y);
                }
                Path2D area = new Path2D.Double(line);
                area.lineTo(PL + (n - 0.5) * slot, chartBottom);
                area.lineTo(PL + 0.5 * slot, chartBottom);
                area.closePath();
                g.setPaint(new GradientPaint(0, PT,
                        new Color(trend.getRed(), trend.getGreen(), trend.getBlue(), 95),
                        0, chartBottom,
                        new Color(trend.getRed(), trend.getGreen(), trend.getBlue(), 0)));
                g.fill(area);
                g.setColor(trend);
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(line);
            } else {
                for (int i = 0; i < n; i++) {
                    Candle c = data.get(i);
                    Color col = c.c >= c.o ? SUCCESS : DANGER;
                    double x = PL + (i + 0.5) * slot;
                    g.setColor(col);
                    g.setStroke(new BasicStroke(1f));
                    g.draw(new Line2D.Double(x, yOf(c.h), x, yOf(c.l)));
                    double top = yOf(Math.max(c.o, c.c)), bot = yOf(Math.min(c.o, c.c));
                    double w = Math.max(1.5, slot * 0.66);
                    g.fill(new Rectangle2D.Double(x - w / 2, top, w, Math.max(1, bot - top)));
                }
            }

            // moving average
            if (showMA) {
                int win = n >= 60 ? 20 : (n >= 20 ? 5 : 3);
                Path2D ma = new Path2D.Double();
                double sum = 0;
                boolean started = false;
                for (int i = 0; i < n; i++) {
                    sum += data.get(i).c;
                    if (i >= win) sum -= data.get(i - win).c;
                    if (i >= win - 1) {
                        double x = PL + (i + 0.5) * slot, y = yOf(sum / win);
                        if (!started) { ma.moveTo(x, y); started = true; } else ma.lineTo(x, y);
                    }
                }
                g.setColor(WARNING);
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(ma);
                g.setFont(SMALL_B);
                g.drawString("— MA" + win, PL + 4, PT + 10);
            }

            // last price line + tag
            double ly = yOf(last.c);
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[]{4f, 4f}, 0f));
            g.setColor(new Color(trend.getRed(), trend.getGreen(), trend.getBlue(), 150));
            g.draw(new Line2D.Double(PL, ly, W - PR, ly));
            tag(g, axis(last.c), W - PR + 2, ly, PR - 6, trend, new Color(10, 14, 20));

            // hover crosshair + tooltip
            if (hover >= 0 && hover < n) {
                Candle c = data.get(hover);
                double x = PL + (hover + 0.5) * slot, y = yOf(c.c);
                g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                        new float[]{3f, 3f}, 0f));
                g.setColor(new Color(200, 210, 235, 110));
                g.draw(new Line2D.Double(x, PT, x, H - PB));
                g.draw(new Line2D.Double(PL, y, W - PR, y));

                tag(g, axis(c.c), W - PR + 2, y, PR - 6, new Color(70, 82, 112), TEXT);
                g.setFont(SMALL_B);
                String ds = c.date.format(F_SHORT);
                int dw = g.getFontMetrics().stringWidth(ds) + 16;
                int dx = (int) Math.max(PL, Math.min(W - PR - dw, x - dw / 2.0));
                g.setColor(new Color(70, 82, 112));
                g.fill(new RoundRectangle2D.Double(dx, H - PB + 4, dw, 18, 6, 6));
                g.setColor(TEXT);
                g.drawString(ds, dx + 8, H - PB + 17);

                g.setStroke(new BasicStroke(1f));
                if (!candle) {
                    g.setColor(BG_CARD);
                    g.fill(new java.awt.geom.Ellipse2D.Double(x - 6, y - 6, 12, 12));
                    g.setColor(c.c >= c.o ? SUCCESS : DANGER);
                    g.fill(new java.awt.geom.Ellipse2D.Double(x - 4, y - 4, 8, 8));
                } else {
                    g.setColor(new Color(255, 255, 255, 18));
                    g.fill(new Rectangle2D.Double(x - slot / 2, PT, slot, H - PB - PT));
                }

                // tooltip box
                String[] labels = valueOnly ? new String[]{"Net worth"} : new String[]{"Open", "High", "Low", "Close", "Volume"};
                String[] vals = valueOnly ? new String[]{inr0(c.c)} : new String[]{num(c.o), num(c.h), num(c.l), num(c.c), compact(c.v)};
                g.setFont(SMALL_B);
                String title = c.date.format(F_FULL);
                int boxW = Math.max(g.getFontMetrics().stringWidth(title) + 24, 150);
                int boxH = 34 + labels.length * 17;
                int bx = (int) x + 16;
                if (bx + boxW > W - PR) bx = (int) x - 16 - boxW;
                bx = Math.max(PL, bx);
                int by = PT + 8;
                g.setColor(new Color(13, 16, 24, 238));
                g.fill(new RoundRectangle2D.Double(bx, by, boxW, boxH, 10, 10));
                g.setColor(BORDER);
                g.draw(new RoundRectangle2D.Double(bx, by, boxW, boxH, 10, 10));
                g.setColor(TEXT);
                g.drawString(title, bx + 12, by + 20);
                g.setFont(SMALL);
                for (int i = 0; i < labels.length; i++) {
                    int ty = by + 38 + i * 17;
                    g.setColor(TEXT_MUTED);
                    g.drawString(labels[i], bx + 12, ty);
                    g.setColor((valueOnly ? i == 0 : i == 3) ? (valueOnly ? ACCENT : (c.c >= c.o ? SUCCESS : DANGER)) : TEXT);
                    g.drawString(vals[i], bx + boxW - 12 - g.getFontMetrics().stringWidth(vals[i]), ty);
                }
            }
            g.dispose();
        }

        void tag(Graphics2D g, String s, int x, double cy, int w, Color bg, Color fg) {
            g.setFont(SMALL_B);
            g.setColor(bg);
            g.fill(new RoundRectangle2D.Double(x, cy - 9, w, 18, 6, 6));
            g.setColor(fg);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(s, x + (w - fm.stringWidth(s)) / 2, (int) cy + fm.getAscent() / 2 - 1);
        }
    }

    // =====================================================================
    //              CHART PANEL (header + controls + chart + stats)
    // =====================================================================
    static class ChartPanel extends JPanel {
        Company cur;
        int days = 66;
        final StockChart chart = new StockChart();
        final JLabel symL = new JLabel("—"), nameL = muted(" "), priceL = new JLabel(" "), chgL = new JLabel(" ");
        final JLabel[] stat = new JLabel[6];
        final String[] statNames = {"DAY HIGH", "DAY LOW", "VOLUME", "RANGE HIGH", "RANGE LOW", "RANGE CHANGE"};

        ChartPanel() {
            setLayout(new BorderLayout(0, 12));
            setBackground(BG_CARD);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    new EmptyBorder(16, 18, 14, 18)));
            setPreferredSize(new Dimension(580, 420));

            // header
            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            JPanel left = new JPanel();
            left.setOpaque(false);
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
            symL.setFont(new Font("SansSerif", Font.BOLD, 24)); symL.setForeground(TEXT);
            symL.setAlignmentX(Component.LEFT_ALIGNMENT);
            nameL.setAlignmentX(Component.LEFT_ALIGNMENT);
            left.add(symL); left.add(Box.createVerticalStrut(2)); left.add(nameL);

            JPanel right = new JPanel();
            right.setOpaque(false);
            right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
            priceL.setFont(new Font("SansSerif", Font.BOLD, 24)); priceL.setForeground(TEXT);
            chgL.setFont(H3);
            priceL.setAlignmentX(Component.RIGHT_ALIGNMENT);
            chgL.setAlignmentX(Component.RIGHT_ALIGNMENT);
            right.add(priceL); right.add(Box.createVerticalStrut(2)); right.add(chgL);
            head.add(left, BorderLayout.WEST);
            head.add(right, BorderLayout.EAST);

            // controls
            JPanel controls = new JPanel(new BorderLayout());
            controls.setOpaque(false);
            JPanel ranges = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            ranges.setOpaque(false);
            ButtonGroup rg = new ButtonGroup();
            String[] rn = {"1W", "1M", "3M", "6M", "1Y"};
            int[] rd = {5, 22, 66, 126, 252};
            for (int i = 0; i < rn.length; i++) {
                Pill p = new Pill(rn[i]);
                final int d = rd[i];
                p.setSelected(d == days);
                p.addActionListener(e -> { days = d; refresh(); });
                rg.add(p); ranges.add(p);
            }
            JPanel types = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            types.setOpaque(false);
            ButtonGroup tg = new ButtonGroup();
            Pill line = new Pill("Line"), candles = new Pill("Candles");
            line.setSelected(true);
            line.addActionListener(e -> { chart.candle = false; chart.repaint(); });
            candles.addActionListener(e -> { chart.candle = true; chart.repaint(); });
            tg.add(line); tg.add(candles);
            Pill ma = new Pill("MA");
            ma.setSelected(true);
            ma.addActionListener(e -> { chart.showMA = ma.isSelected(); chart.repaint(); });
            types.add(line); types.add(candles); types.add(Box.createHorizontalStrut(6)); types.add(ma);
            controls.add(ranges, BorderLayout.WEST);
            controls.add(types, BorderLayout.EAST);

            JPanel north = new JPanel();
            north.setOpaque(false);
            north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
            north.add(head); north.add(Box.createVerticalStrut(12)); north.add(controls);
            add(north, BorderLayout.NORTH);
            add(chart, BorderLayout.CENTER);

            // stats
            JPanel st = new JPanel(new GridLayout(2, 3, 10, 8));
            st.setOpaque(false);
            st.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                    new EmptyBorder(10, 0, 0, 0)));
            for (int i = 0; i < 6; i++) { stat[i] = new JLabel(); stat[i].setFont(BODY); st.add(stat[i]); }
            add(st, BorderLayout.SOUTH);
            refresh();
        }

        void setCompany(Company c) { cur = c; refresh(); }

        void setStat(int i, String value, Color color) {
            stat[i].setText("<html><span style='color:#96a0b9;font-size:9px;'>" + statNames[i]
                    + "</span><br><span style='font-size:12px;font-weight:bold;color:" + hex(color) + ";'>"
                    + value + "</span></html>");
        }

        void refresh() {
            if (cur == null) {
                chart.setData(new ArrayList<>());
                symL.setText("—"); nameL.setText("No stock selected");
                priceL.setText(" "); chgL.setText(" ");
                for (int i = 0; i < 6; i++) setStat(i, "—", TEXT);
                return;
            }
            List<Candle> all = history(cur);
            int from = Math.max(0, all.size() - days);
            List<Candle> sub = new ArrayList<>(all.subList(from, all.size()));
            chart.setData(sub);
            Candle last = sub.get(sub.size() - 1);

            symL.setText(cur.symbol);
            nameL.setText(cur.name + "  ·  " + cur.sector + "  ·  " + countryFlag(cur.country));
            priceL.setText(inr(cur.price));
            chgL.setText((cur.changePct >= 0 ? "▲ " : "▼ ") + pct(cur.changePct) + " today");
            chgL.setForeground(cur.changePct >= 0 ? SUCCESS : DANGER);

            double hi = -Double.MAX_VALUE, lo = Double.MAX_VALUE;
            for (Candle c : sub) { hi = Math.max(hi, c.h); lo = Math.min(lo, c.l); }
            double chg = (last.c / sub.get(0).o - 1) * 100;
            setStat(0, num(last.h), TEXT);
            setStat(1, num(last.l), TEXT);
            setStat(2, compact(last.v), TEXT);
            setStat(3, num(hi), TEXT);
            setStat(4, num(lo), TEXT);
            setStat(5, pct(chg), chg >= 0 ? SUCCESS : DANGER);
        }
    }

    // =====================================================================
    //                         ALLOCATION DONUT
    // =====================================================================
    static class DonutChart extends JComponent {
        final List<Holding> hs;
        static final Color[] PAL = {ACCENT, SUCCESS, WARNING, new Color(190, 120, 255),
                new Color(255, 120, 170), new Color(80, 215, 230), new Color(255, 150, 80),
                new Color(130, 140, 170)};

        DonutChart(List<Holding> hs) {
            this.hs = hs;
            setOpaque(false);
            setPreferredSize(new Dimension(300, 220));
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int W = getWidth(), H = getHeight();

            List<Object[]> items = new ArrayList<>();
            double total = 0;
            for (Holding h : hs) {
                Company c = findCompany(h.symbol);
                double v = h.qty * (c != null ? c.price : h.avgPrice);
                items.add(new Object[]{h.symbol, v});
                total += v;
            }
            if (total <= 0) {
                g.setFont(BODY);
                g.setColor(TEXT_MUTED);
                String a = "No holdings yet", b = "Place a trade to see allocation";
                FontMetrics fm = g.getFontMetrics();
                g.drawString(a, (W - fm.stringWidth(a)) / 2, H / 2 - 4);
                g.setFont(SMALL);
                fm = g.getFontMetrics();
                g.drawString(b, (W - fm.stringWidth(b)) / 2, H / 2 + 14);
                g.dispose();
                return;
            }
            items.sort((x, y) -> Double.compare((Double) y[1], (Double) x[1]));
            if (items.size() > 7) {
                double rest = 0;
                for (int i = 7; i < items.size(); i++) rest += (Double) items.get(i)[1];
                items = new ArrayList<>(items.subList(0, 7));
                items.add(new Object[]{"Others", rest});
            }

            int size = Math.min(H - 24, (int) (W * 0.45));
            int ring = 22;
            int x = 14, y = (H - size) / 2;
            double start = 90;
            g.setStroke(new BasicStroke(ring, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
            for (int i = 0; i < items.size(); i++) {
                double frac = (Double) items.get(i)[1] / total;
                double ext = -360.0 * frac;
                if (items.size() > 1 && Math.abs(ext) > 4) ext += 1.5;
                g.setColor(PAL[i % PAL.length]);
                g.draw(new Arc2D.Double(x + ring / 2.0, y + ring / 2.0, size - ring, size - ring,
                        start, ext, Arc2D.OPEN));
                start += -360.0 * frac;
            }
            // centre text
            g.setFont(SMALL);
            g.setColor(TEXT_MUTED);
            String c1 = "INVESTED";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(c1, x + (size - fm.stringWidth(c1)) / 2, y + size / 2 - 4);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.setColor(TEXT);
            String c2 = "Rs." + compact(total);
            fm = g.getFontMetrics();
            g.drawString(c2, x + (size - fm.stringWidth(c2)) / 2, y + size / 2 + 15);

            // legend
            int lx = x + size + 22;
            int rowH = 22;
            int ly = Math.max(14, (H - items.size() * rowH) / 2) + 12;
            for (int i = 0; i < items.size(); i++) {
                int yy = ly + i * rowH;
                g.setColor(PAL[i % PAL.length]);
                g.fill(new RoundRectangle2D.Double(lx, yy - 9, 10, 10, 3, 3));
                g.setFont(SMALL_B);
                g.setColor(TEXT);
                g.drawString((String) items.get(i)[0], lx + 18, yy);
                String p = String.format("%.1f%%", (Double) items.get(i)[1] / total * 100);
                g.setFont(SMALL);
                g.setColor(TEXT_MUTED);
                g.drawString(p, W - 12 - g.getFontMetrics().stringWidth(p), yy);
            }
            g.dispose();
        }
    }

    // =====================================================================
    //                 STOCK ANALYSIS (powers the Stock Finder)
    // =====================================================================
    static final Map<String, String> DESC = new HashMap<>();
    static {
        DESC.put("RELIANCE", "Oil refining, petrochemicals, retail and Jio telecom. One of India's largest conglomerates.");
        DESC.put("TCS", "India's largest IT services exporter. Builds and runs software for global banks and retailers.");
        DESC.put("INFY", "IT outsourcing and consulting company serving large clients worldwide.");
        DESC.put("HDFCBANK", "India's largest private bank: loans, savings accounts and credit cards.");
        DESC.put("WIPRO", "IT services and consulting company with global clients.");
        DESC.put("ICICIBANK", "Large private bank with a strong retail lending business.");
        DESC.put("BHARTIARTL", "Telecom operator offering mobile, broadband and TV, with operations in Africa.");
        DESC.put("LT", "Engineering and construction: bridges, metros, power plants and defence projects.");
        DESC.put("SBIN", "India's biggest government-owned bank with a huge branch network.");
        DESC.put("ASIANPAINT", "India's market leader in home and decorative paints.");
        DESC.put("TATAMOTORS", "Makes cars, SUVs, electric vehicles and trucks; owns Jaguar Land Rover.");
        DESC.put("MARUTI", "India's top passenger-car maker, part of the Suzuki group.");
        DESC.put("AAPL", "Maker of iPhone, Mac and iPad, plus services like the App Store and iCloud.");
        DESC.put("MSFT", "Windows, Office, Azure cloud computing and AI tools for businesses.");
        DESC.put("GOOGL", "Google Search, YouTube, Android and Google Cloud. Earns mostly from advertising.");
        DESC.put("AMZN", "Online shopping, Prime and AWS, the world's biggest cloud platform.");
        DESC.put("TSLA", "Electric vehicles, batteries and solar. Popular but very volatile.");
        DESC.put("NVDA", "Designs the AI and gaming chips (GPUs) that power modern data centers.");
        DESC.put("HSBC", "Global bank focused on Asia and the UK.");
        DESC.put("BP", "Oil and gas major that is investing in renewable energy.");
        DESC.put("AZN", "Pharmaceutical company: cancer, heart and respiratory medicines.");
        DESC.put("SONY", "PlayStation, music, movies and camera sensors.");
        DESC.put("TM", "One of the world's largest carmakers, known for reliability and hybrids.");
        DESC.put("SAP", "Business software used by large companies to run finance and supply chains.");
        DESC.put("BMW", "German premium cars and motorcycles.");
        DESC.put("BABA", "China's biggest e-commerce and cloud company.");
        DESC.put("JD", "Chinese online retailer with its own delivery network.");
    }

    static class Pick {
        Company c;
        double ret1y, ret3m, vol, dd, trendPct, score;
        String risk, trend, why;
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    static Pick analyze(Company c, String profile) {
        List<Candle> all = history(c);
        int n = all.size(), from = n - 252;
        Pick p = new Pick();
        p.c = c;
        double first = all.get(from).c, last = all.get(n - 1).c;
        p.ret1y = (last / first - 1) * 100;
        p.ret3m = (last / all.get(n - 66).c - 1) * 100;
        double sum = 0, sq = 0, peak = first, dd = 0;
        int k = 0;
        for (int i = from + 1; i < n; i++) {
            double r = all.get(i).c / all.get(i - 1).c - 1;
            sum += r; sq += r * r; k++;
            peak = Math.max(peak, all.get(i).c);
            dd = Math.max(dd, (peak - all.get(i).c) / peak);
        }
        double mean = sum / k;
        p.vol = Math.sqrt(Math.max(0, sq / k - mean * mean)) * Math.sqrt(252) * 100;
        p.dd = dd * 100;
        double ma = 0;
        for (int i = n - 50; i < n; i++) ma += all.get(i).c;
        ma /= 50;
        p.trendPct = (last / ma - 1) * 100;
        p.risk = p.vol < 22 ? "Low" : p.vol < 30 ? "Medium" : "High";
        p.trend = p.trendPct > 2 ? "Uptrend" : p.trendPct < -2 ? "Downtrend" : "Sideways";

        double riskS = clamp(100 - (p.vol - 15) * 3.5, 0, 100);
        double ddS = clamp(100 - p.dd * 2.5, 0, 100);
        double retS = clamp(50 + p.ret1y * 1.5, 0, 100);
        double trS = clamp(50 + p.trendPct * 8, 0, 100);
        double volB = clamp((p.vol - 15) * 5, 0, 100);
        switch (profile) {
            case "Conservative": p.score = .40 * riskS + .30 * ddS + .15 * retS + .15 * trS; break;
            case "Aggressive":   p.score = .45 * retS + .35 * trS + .20 * volB; break;
            default:             p.score = .25 * riskS + .10 * ddS + .35 * retS + .30 * trS;
        }
        String tr = p.trend.equals("Uptrend") ? "trading above its 50-day average (positive momentum)"
                : p.trend.equals("Downtrend") ? "trading below its 50-day average (weak momentum)"
                : "moving sideways around its 50-day average";
        p.why = (p.vol < 22 ? "Calm" : p.vol < 30 ? "Moderate" : "Big") + " price swings ("
                + String.format("%.0f%%", p.vol) + " yearly volatility), "
                + (p.ret1y >= 0 ? "up " : "down ") + String.format("%.1f%%", Math.abs(p.ret1y))
                + " over the past year, and " + tr + ". Worst drop from a peak: "
                + String.format("%.1f%%", p.dd) + ".";
        return p;
    }

    // Coloured tag renderer for Risk / Trend / Match-score cells
    static class TagR extends CellR {
        final boolean score;
        TagR(boolean score) { super(SwingConstants.CENTER, false); this.score = score; }
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                  boolean f, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, f, r, c);
            String s = v == null ? "" : v.toString();
            Color col = TEXT;
            if (score) {
                try {
                    int x = Integer.parseInt(s.trim());
                    col = x >= 65 ? SUCCESS : x >= 50 ? WARNING : DANGER;
                } catch (NumberFormatException ignored) {}
            } else if (s.equals("Low") || s.equals("Uptrend")) col = SUCCESS;
            else if (s.equals("Medium") || s.equals("Sideways")) col = WARNING;
            else if (s.equals("High") || s.equals("Downtrend")) col = DANGER;
            if (!sel) setForeground(col);
            setFont(H3);
            return this;
        }
    }

    // =====================================================================
    //          DASHBOARD / WELCOME BUILDING BLOCKS
    // =====================================================================
    static class KpiCard extends JComponent {
        final String title; final Color accent;
        String value = "-", sub = " ";
        Color valueColor = TEXT, subColor = TEXT_MUTED, sparkColor = ACCENT;
        double[] spark; double progress = -1;

        KpiCard(String title, Color accent) {
            this.title = title; this.accent = accent;
            setOpaque(false);
            setPreferredSize(new Dimension(200, 118));
        }
        void set(String value, Color valueColor, String sub, Color subColor,
                 double[] spark, Color sparkColor, double progress) {
            this.value = value; this.valueColor = valueColor; this.sub = sub; this.subColor = subColor;
            this.spark = spark; this.sparkColor = sparkColor; this.progress = progress;
            repaint();
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int W = getWidth(), H = getHeight();
            g.setColor(BG_CARD);
            g.fill(new RoundRectangle2D.Double(0, 0, W - 1, H - 1, 16, 16));
            g.setColor(BORDER);
            g.draw(new RoundRectangle2D.Double(0, 0, W - 1, H - 1, 16, 16));
            // accent glow dot + title
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
            g.fillOval(14, 15, 16, 16);
            g.setColor(accent);
            g.fillOval(18, 19, 8, 8);
            g.setFont(SMALL_B);
            g.setColor(TEXT_MUTED);
            g.drawString(title, 38, 28);
            // value
            g.setFont(new Font("SansSerif", Font.BOLD, 25));
            g.setColor(valueColor);
            g.drawString(value, 18, 64);
            // sparkline
            if (spark != null && spark.length > 1) {
                double mn = Double.MAX_VALUE, mx = -Double.MAX_VALUE;
                for (double v : spark) { mn = Math.min(mn, v); mx = Math.max(mx, v); }
                if (mx - mn < 1e-9) { mx += 1; mn -= 1; }
                int sx = (int) (W * 0.52), sw = W - sx - 16, sy = 38, sh = H - 38 - 34;
                Path2D line = new Path2D.Double();
                for (int i = 0; i < spark.length; i++) {
                    double x = sx + sw * (double) i / (spark.length - 1);
                    double y = sy + (mx - spark[i]) / (mx - mn) * sh;
                    if (i == 0) line.moveTo(x, y); else line.lineTo(x, y);
                }
                Path2D area = new Path2D.Double(line);
                area.lineTo(sx + sw, sy + sh + 6);
                area.lineTo(sx, sy + sh + 6);
                area.closePath();
                g.setPaint(new GradientPaint(0, sy, new Color(sparkColor.getRed(), sparkColor.getGreen(), sparkColor.getBlue(), 80),
                        0, sy + sh + 6, new Color(sparkColor.getRed(), sparkColor.getGreen(), sparkColor.getBlue(), 0)));
                g.fill(area);
                g.setColor(sparkColor);
                g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(line);
            }
            // sub text / progress
            g.setFont(SMALL_B);
            g.setColor(subColor);
            g.drawString(sub, 18, progress >= 0 ? H - 28 : H - 16);
            if (progress >= 0) {
                int bx = 18, bw = W - 36, by = H - 18;
                g.setColor(BG_INPUT);
                g.fillRoundRect(bx, by, bw, 5, 5, 5);
                g.setColor(accent);
                g.fillRoundRect(bx, by, (int) (bw * clamp(progress, 0, 1)), 5, 5, 5);
            }
            g.dispose();
        }
    }

    static class TickerTape extends JComponent {
        double off = 0;
        final javax.swing.Timer timer;
        final boolean translucent;
        TickerTape(boolean translucent) {
            this.translucent = translucent;
            setOpaque(false);
            timer = new javax.swing.Timer(30, e -> { off += 1.3; repaint(); });
        }
        @Override public void addNotify() { super.addNotify(); timer.start(); }
        @Override public void removeNotify() { timer.stop(); super.removeNotify(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int W = getWidth(), H = getHeight();
            g.setColor(translucent ? new Color(0, 0, 0, 80) : BG_PANEL);
            g.fillRect(0, 0, W, H);
            g.setColor(translucent ? new Color(255, 255, 255, 25) : BORDER);
            g.drawLine(0, 0, W, 0);
            g.drawLine(0, H - 1, W, H - 1);
            Font fb = SMALL_B, fp = SMALL;
            int gap = 38;
            int total = 0;
            int[] wd = new int[COMPANIES.size()];
            for (int i = 0; i < COMPANIES.size(); i++) {
                Company c = COMPANIES.get(i);
                wd[i] = g.getFontMetrics(fb).stringWidth(c.symbol) + 10 + g.getFontMetrics(fp).stringWidth(inr(c.price))
                        + 10 + g.getFontMetrics(fb).stringWidth("\u25B2 " + pct(c.changePct)) + gap;
                total += wd[i];
            }
            double x = -(off % total);
            int base = H / 2 + 4;
            while (x < W) {
                for (int i = 0; i < COMPANIES.size(); i++) {
                    Company c = COMPANIES.get(i);
                    double cx = x;
                    g.setFont(fb); g.setColor(TEXT);
                    g.drawString(c.symbol, (int) cx, base);
                    cx += g.getFontMetrics().stringWidth(c.symbol) + 10;
                    g.setFont(fp); g.setColor(TEXT_MUTED);
                    g.drawString(inr(c.price), (int) cx, base);
                    cx += g.getFontMetrics().stringWidth(inr(c.price)) + 10;
                    g.setFont(fb); g.setColor(c.changePct >= 0 ? SUCCESS : DANGER);
                    g.drawString((c.changePct >= 0 ? "\u25B2 " : "\u25BC ") + pct(c.changePct), (int) cx, base);
                    x += wd[i];
                }
            }
            g.dispose();
        }
    }

    static class EmptyStack extends JPanel {
        final CardLayout cl = new CardLayout();
        EmptyStack(JComponent content, JComponent empty) {
            setLayout(cl);
            setOpaque(false);
            add(content, "C");
            add(empty, "E");
        }
        void showEmpty(boolean e) { cl.show(this, e ? "E" : "C"); }
    }

    static class TypeR extends CellR {
        TypeR() { super(SwingConstants.LEFT, false); }
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                  boolean f, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, f, r, c);
            if (!sel) setForeground("BUY".equals(v) ? SUCCESS : DANGER);
            setFont(H3);
            return this;
        }
    }

    static double holdingValue(Holding h) {
        Company c = findCompany(h.symbol);
        return h.qty * (c != null ? c.price : h.avgPrice);
    }
    static String inr0(double v) { return "Rs." + String.format("%,.0f", v); }
    static String signedInr0(double v) { return (v >= 0 ? "+" : "-") + "Rs." + String.format("%,.0f", Math.abs(v)); }
    static Color colorFor(double v) { return v > 0.005 ? SUCCESS : v < -0.005 ? DANGER : TEXT_MUTED; }
    static double[] tail(double[] s, int k) { return Arrays.copyOfRange(s, Math.max(0, s.length - k), s.length); }

    // =====================================================================
    //                         WELCOME SCREEN
    // =====================================================================
    static class BadgeLabel extends JLabel {
        BadgeLabel(String s) {
            super(s);
            setFont(SMALL_B);
            setForeground(new Color(170, 200, 255));
            setBorder(new EmptyBorder(7, 16, 7, 16));
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(99, 155, 255, 40));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g.setColor(new Color(99, 155, 255, 110));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g.dispose();
            super.paintComponent(g0);
        }
    }

    static class FeatureCard extends JComponent {
        final String icon, title, desc; final Color accent; boolean hover;
        FeatureCard(String icon, String title, String desc, Color accent) {
            this.icon = icon; this.title = title; this.desc = desc; this.accent = accent;
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int W = getWidth(), H = getHeight();
            g.setColor(new Color(255, 255, 255, hover ? 28 : 14));
            g.fill(new RoundRectangle2D.Double(0, 0, W - 1, H - 1, 18, 18));
            g.setColor(new Color(255, 255, 255, hover ? 70 : 36));
            g.draw(new RoundRectangle2D.Double(0, 0, W - 1, H - 1, 18, 18));
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 55));
            g.fillOval(20, 18, 38, 38);
            g.setColor(accent);
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(icon, 39 - fm.stringWidth(icon) / 2, 43);
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.setColor(TEXT);
            g.drawString(title, 70, 42);
            g.setFont(SMALL);
            g.setColor(new Color(165, 178, 208));
            fm = g.getFontMetrics();
            int y = 78, maxW = W - 40;
            StringBuilder line = new StringBuilder();
            for (String w : desc.split(" ")) {
                if (fm.stringWidth(line + w) > maxW) { g.drawString(line.toString().trim(), 20, y); y += 17; line = new StringBuilder(); }
                line.append(w).append(' ');
            }
            g.drawString(line.toString().trim(), 20, y);
            g.dispose();
        }
    }

    static class WelcomePanel extends JPanel {
        final JFrame owner;
        double t = 0;
        final javax.swing.Timer timer;
        final double[] w1 = walk(11, 240), w2 = walk(29, 240);
        final Company nv = findCompany("NVDA");
        final List<Candle> nvh = history(nv);
        final Pick nvPick = analyze(nv, "Aggressive");

        static double[] walk(long seed, int n) {
            Random r = new Random(seed);
            double[] a = new double[n];
            double v = 0;
            for (int i = 0; i < n; i++) { v += r.nextGaussian() * 0.9 + 0.14; a[i] = v; }
            double mn = Double.MAX_VALUE, mx = -Double.MAX_VALUE;
            for (double d : a) { mn = Math.min(mn, d); mx = Math.max(mx, d); }
            for (int i = 0; i < n; i++) a[i] = (a[i] - mn) / (mx - mn);
            return a;
        }

        WelcomePanel(JFrame owner) {
            this.owner = owner;
            setLayout(new BorderLayout());
            setBackground(BG_DEEP);
            add(nav(), BorderLayout.NORTH);
            add(hero(), BorderLayout.CENTER);
            add(bottom(), BorderLayout.SOUTH);
            timer = new javax.swing.Timer(33, e -> { t += 0.033; repaint(); });
        }
        @Override public void addNotify() { super.addNotify(); timer.start(); }
        @Override public void removeNotify() { timer.stop(); super.removeNotify(); }

        void goLogin() { new LoginScreen().setVisible(true); if (owner != null) owner.dispose(); }
        void goDemo() {
            LoginScreen login = new LoginScreen();
            login.userField.setText("investor02@example.test");
            login.passField.setText("Demo@123");
            login.setVisible(true);
            if (owner != null) owner.dispose();
        }

        JPanel nav() {
            JPanel nav = new JPanel(new BorderLayout());
            nav.setOpaque(false);
            nav.setBorder(new EmptyBorder(22, 48, 0, 48));
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            left.setOpaque(false);
            JLabel logo = new JLabel("\u25C8");
            logo.setFont(new Font("SansSerif", Font.BOLD, 30));
            logo.setForeground(ACCENT);
            JLabel name = new JLabel("SSA Pro");
            name.setFont(new Font("SansSerif", Font.BOLD, 21));
            name.setForeground(Color.WHITE);
            left.add(logo); left.add(name);
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            right.setOpaque(false);
            JButton signIn = btn("Sign In", new Color(255, 255, 255, 34));
            signIn.addActionListener(e -> goLogin());
            JButton start = btn("Get Started", ACCENT);
            start.addActionListener(e -> goLogin());
            right.add(signIn); right.add(start);
            nav.add(left, BorderLayout.WEST);
            nav.add(right, BorderLayout.EAST);
            return nav;
        }

        JPanel hero() {
            JPanel hero = new JPanel(new GridBagLayout());
            hero.setOpaque(false);
            JPanel col = new JPanel();
            col.setOpaque(false);
            col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

            BadgeLabel badge = new BadgeLabel("\u25C8   SMART STOCK ANALYZER   \u00B7   TRADING SIMULATOR");
            badge.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel head = new JLabel("<html><div style='font-size:40pt;font-weight:bold;color:#f0f4fc;'>Invest smarter.<br>"
                    + "<span style='color:#639bff;'>Trade with confidence.</span></div></html>");
            head.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel sub = new JLabel("<html><div style='width:470px;color:#9fb0d4;font-size:12pt;'>"
                    + "A professional-grade terminal to practice investing with virtual money. Explore global markets, "
                    + "read interactive charts, and let the Stock Finder match you with companies that fit your style."
                    + "</div></html>");
            sub.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            btns.setOpaque(false);
            btns.setAlignmentX(Component.LEFT_ALIGNMENT);
            JButton go = btn("Get Started   \u2192", ACCENT);
            go.setFont(new Font("SansSerif", Font.BOLD, 15));
            go.setBorder(new EmptyBorder(14, 30, 14, 30));
            go.addActionListener(e -> goLogin());
            JButton demo = btn("Try Demo Account", new Color(255, 255, 255, 34));
            demo.setFont(new Font("SansSerif", Font.BOLD, 15));
            demo.setBorder(new EmptyBorder(14, 28, 14, 28));
            demo.addActionListener(e -> goDemo());
            btns.add(go); btns.add(Box.createHorizontalStrut(12)); btns.add(demo);

            JPanel stats = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            stats.setOpaque(false);
            stats.setAlignmentX(Component.LEFT_ALIGNMENT);
            stats.add(stat(String.valueOf(COMPANIES.size()), "Companies")); stats.add(Box.createHorizontalStrut(38));
            stats.add(stat("6", "Countries")); stats.add(Box.createHorizontalStrut(38));
            stats.add(stat("Rs.1,00,000", "Virtual cash")); stats.add(Box.createHorizontalStrut(38));
            stats.add(stat("1Y", "Chart history"));

            col.add(badge); col.add(Box.createVerticalStrut(22));
            col.add(head); col.add(Box.createVerticalStrut(18));
            col.add(sub); col.add(Box.createVerticalStrut(28));
            col.add(btns); col.add(Box.createVerticalStrut(34));
            col.add(stats);

            GridBagConstraints gc = new GridBagConstraints();
            gc.anchor = GridBagConstraints.WEST;
            gc.weightx = 1;
            gc.insets = new Insets(0, 48, 0, 0);
            hero.add(col, gc);
            return hero;
        }

        JPanel stat(String v, String l) {
            JPanel p = new JPanel();
            p.setOpaque(false);
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            JLabel a = new JLabel(v);
            a.setFont(new Font("SansSerif", Font.BOLD, 22));
            a.setForeground(TEXT);
            JLabel b = muted(l);
            p.add(a); p.add(b);
            return p;
        }

        JPanel bottom() {
            JPanel b = new JPanel();
            b.setOpaque(false);
            b.setLayout(new BoxLayout(b, BoxLayout.Y_AXIS));
            JPanel feats = new JPanel(new GridLayout(1, 3, 16, 0));
            feats.setOpaque(false);
            feats.setBorder(new EmptyBorder(0, 48, 18, 48));
            feats.setPreferredSize(new Dimension(100, 136));
            feats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 136));
            feats.add(new FeatureCard("\u25C8", "Global Markets",
                    "Trade companies from India, USA, UK, Japan, Germany and China in one place.", ACCENT));
            feats.add(new FeatureCard("\u25B2", "Interactive Charts",
                    "Line and candlestick views, moving averages, volume and a crosshair for every day.", SUCCESS));
            feats.add(new FeatureCard("\u2605", "Smart Stock Finder",
                    "Get ranked picks and a starter portfolio matched to your risk style and budget.", WARNING));
            TickerTape tape = new TickerTape(true);
            tape.setPreferredSize(new Dimension(100, 36));
            tape.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            JLabel foot = new JLabel("\u00A9 2026 SSA Pro   \u00B7   Educational simulator. All prices and charts are simulated; not financial advice.");
            foot.setFont(SMALL);
            foot.setForeground(new Color(120, 132, 165));
            JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            footer.setOpaque(false);
            footer.setBorder(new EmptyBorder(9, 0, 11, 0));
            footer.add(foot);
            b.add(feats); b.add(tape); b.add(footer);
            return b;
        }

        void drawWalk(Graphics2D g, double[] a, int W, int H, double top, double height,
                      Color c, int lineA, int fillA, float stroke, double prog, boolean dot) {
            int N = a.length, last = (int) Math.max(1, (N - 1) * prog);
            Path2D path = new Path2D.Double();
            double lx = 0, ly = 0;
            for (int i = 0; i <= last; i++) {
                lx = W * (double) i / (N - 1);
                ly = top + (1 - a[i]) * height;
                if (i == 0) path.moveTo(lx, ly); else path.lineTo(lx, ly);
            }
            Path2D area = new Path2D.Double(path);
            area.lineTo(lx, H); area.lineTo(0, H); area.closePath();
            g.setPaint(new GradientPaint(0, (float) top, new Color(c.getRed(), c.getGreen(), c.getBlue(), fillA),
                    0, H, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)));
            g.fill(area);
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), lineA));
            g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path);
            if (dot) {
                double pulse = (Math.sin(t * 4) + 1) / 2;
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (90 * (1 - pulse))));
                double r = 6 + pulse * 14;
                g.fill(new java.awt.geom.Ellipse2D.Double(lx - r, ly - r, 2 * r, 2 * r));
                g.setColor(Color.WHITE);
                g.fill(new java.awt.geom.Ellipse2D.Double(lx - 4, ly - 4, 8, 8));
            }
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int W = getWidth(), H = getHeight();
            g.setPaint(new GradientPaint(0, 0, new Color(8, 12, 26), W, H, new Color(22, 34, 78)));
            g.fillRect(0, 0, W, H);
            g.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Float(W * 0.76f, H * 0.28f), 480f,
                    new float[]{0f, 1f}, new Color[]{new Color(99, 155, 255, 80), new Color(99, 155, 255, 0)}));
            g.fillRect(0, 0, W, H);
            g.setColor(new Color(255, 255, 255, 7));
            g.setStroke(new BasicStroke(1f));
            for (int x = 0; x < W; x += 80) g.drawLine(x, 0, x, H);
            for (int y = 0; y < H; y += 80) g.drawLine(0, y, W, y);

            double x = clamp(t / 3.0, 0, 1);
            double prog = 1 - Math.pow(1 - x, 3);
            drawWalk(g, w2, W, H, H * 0.52, H * 0.26, SUCCESS, 90, 40, 2f, prog, false);
            drawWalk(g, w1, W, H, H * 0.40, H * 0.36, ACCENT, 190, 70, 3f, prog, prog >= 1);
            drawPreview(g, W);
            g.dispose();
        }

        void drawPreview(Graphics2D g, int W) {
            int cw = 410, ch = 262;
            int x0 = W - cw - 72;
            int y0 = (int) (92 + Math.sin(t * 1.2) * 6);
            g.setColor(new Color(0, 0, 0, 80));
            g.fill(new RoundRectangle2D.Double(x0 + 6, y0 + 14, cw, ch, 24, 24));
            g.setColor(new Color(28, 33, 46, 245));
            g.fill(new RoundRectangle2D.Double(x0, y0, cw, ch, 24, 24));
            g.setColor(new Color(255, 255, 255, 34));
            g.draw(new RoundRectangle2D.Double(x0, y0, cw, ch, 24, 24));

            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.setColor(TEXT);
            g.drawString(nv.symbol, x0 + 24, y0 + 40);
            g.setFont(SMALL);
            g.setColor(TEXT_MUTED);
            g.drawString(nv.name + "  \u00B7  " + nv.sector, x0 + 24, y0 + 58);
            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            g.setColor(TEXT);
            String pr = inr(nv.price);
            g.drawString(pr, x0 + cw - 24 - g.getFontMetrics().stringWidth(pr), y0 + 40);
            g.setFont(SMALL_B);
            g.setColor(nv.changePct >= 0 ? SUCCESS : DANGER);
            String chg = (nv.changePct >= 0 ? "\u25B2 " : "\u25BC ") + pct(nv.changePct);
            g.drawString(chg, x0 + cw - 24 - g.getFontMetrics().stringWidth(chg), y0 + 58);

            int n = 90, from = nvh.size() - n;
            double mn = Double.MAX_VALUE, mx = -Double.MAX_VALUE;
            for (int i = from; i < nvh.size(); i++) { mn = Math.min(mn, nvh.get(i).c); mx = Math.max(mx, nvh.get(i).c); }
            int sx = x0 + 24, sw = cw - 48, sy = y0 + 80, sh = 100;
            Path2D line = new Path2D.Double();
            double ex = 0, ey = 0;
            for (int i = 0; i < n; i++) {
                ex = sx + sw * (double) i / (n - 1);
                ey = sy + (mx - nvh.get(from + i).c) / (mx - mn) * sh;
                if (i == 0) line.moveTo(ex, ey); else line.lineTo(ex, ey);
            }
            Path2D area = new Path2D.Double(line);
            area.lineTo(ex, sy + sh + 8); area.lineTo(sx, sy + sh + 8); area.closePath();
            g.setPaint(new GradientPaint(0, sy, new Color(76, 217, 145, 90), 0, sy + sh + 8, new Color(76, 217, 145, 0)));
            g.fill(area);
            g.setColor(SUCCESS);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(line);
            g.setColor(Color.WHITE);
            g.fill(new java.awt.geom.Ellipse2D.Double(ex - 4, ey - 4, 8, 8));

            String[] lab = {"1Y RETURN", "RISK", "TREND"};
            String[] val = {pct(nvPick.ret1y), nvPick.risk, nvPick.trend};
            Color[] col = {nvPick.ret1y >= 0 ? SUCCESS : DANGER,
                    nvPick.risk.equals("Low") ? SUCCESS : nvPick.risk.equals("Medium") ? WARNING : DANGER,
                    nvPick.trend.equals("Uptrend") ? SUCCESS : nvPick.trend.equals("Downtrend") ? DANGER : WARNING};
            for (int i = 0; i < 3; i++) {
                int tx = x0 + 24 + i * 124;
                g.setFont(SMALL);
                g.setColor(TEXT_MUTED);
                g.drawString(lab[i], tx, y0 + 218);
                g.setFont(new Font("SansSerif", Font.BOLD, 14));
                g.setColor(col[i]);
                g.drawString(val[i], tx, y0 + 238);
            }

            // floating badge: sample portfolio
            int bw = 214, bh = 78;
            int bx = x0 + cw - bw + 34, by = (int) (y0 + ch + 18 - Math.sin(t * 1.2 + 1.5) * 7);
            g.setColor(new Color(0, 0, 0, 80));
            g.fill(new RoundRectangle2D.Double(bx + 4, by + 10, bw, bh, 18, 18));
            g.setColor(new Color(36, 43, 60, 248));
            g.fill(new RoundRectangle2D.Double(bx, by, bw, bh, 18, 18));
            g.setColor(new Color(255, 255, 255, 40));
            g.draw(new RoundRectangle2D.Double(bx, by, bw, bh, 18, 18));
            g.setFont(SMALL);
            g.setColor(TEXT_MUTED);
            g.drawString("SAMPLE PORTFOLIO", bx + 18, by + 24);
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.setColor(TEXT);
            g.drawString("Rs.1,24,560", bx + 18, by + 49);
            g.setFont(SMALL_B);
            g.setColor(SUCCESS);
            g.drawString("\u25B2 +24.56% this year", bx + 18, by + 67);

            // live pill
            String lp = "\u25CF  Simulated live";
            g.setFont(SMALL_B);
            int lw = g.getFontMetrics().stringWidth(lp) + 24;
            int lx = x0 + cw - lw + 14, ly = (int) (y0 - 18 + Math.sin(t * 1.2 + 3) * 4);
            g.setColor(new Color(20, 60, 45, 245));
            g.fill(new RoundRectangle2D.Double(lx, ly, lw, 28, 28, 28));
            g.setColor(new Color(76, 217, 145, 130));
            g.draw(new RoundRectangle2D.Double(lx, ly, lw, 28, 28, 28));
            g.setColor(SUCCESS);
            g.drawString(lp, lx + 12, ly + 18);
        }
    }

    static class WelcomeScreen extends JFrame {
        WelcomeScreen() {
            super("SSA Pro - Welcome");
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(1200, 760);
            setMinimumSize(new Dimension(1100, 700));
            setLocationRelativeTo(null);
            setContentPane(new WelcomePanel(this));
        }
    }

    // ---------- MAIN ----------
    public static void main(String[] args) {
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Panel.background", BG_PANEL);
        UIManager.put("OptionPane.background", BG_CARD);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("ComboBox.background", BG_INPUT);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", new Color(45, 75, 140));
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("ToolTip.background", BG_DEEP);
        UIManager.put("ToolTip.foreground", TEXT);
        SwingUtilities.invokeLater(() -> new WelcomeScreen().setVisible(true));
    }

    // ==================== LOGIN ====================
    static class LoginScreen extends JFrame {
        JTextField userField = input();
        JPasswordField passField = passInput();
        JLabel errorLabel = new JLabel(" ");
        String selectedRole = "USER";

        LoginScreen() {
            super("SSA Pro - Login");
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(900, 610);
            setLocationRelativeTo(null);
            setResizable(false);

            JPanel root = new JPanel(new GridLayout(1, 2));
            root.setBackground(BG_DEEP);
            root.add(brandPanel());
            root.add(formPanel());
            setContentPane(root);
        }

        JPanel brandPanel() {
            JPanel p = new JPanel() {
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setPaint(new GradientPaint(0, 0, new Color(22, 34, 76), getWidth(), getHeight(),
                            new Color(40, 62, 130)));
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.dispose();
                }
            };
            p.setOpaque(false);
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBorder(new EmptyBorder(60, 50, 60, 50));

            JLabel logo = new JLabel("\u25C8");
            logo.setFont(new Font("SansSerif", Font.BOLD, 64));
            logo.setForeground(ACCENT);
            logo.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel title = new JLabel("SSA Pro");
            title.setFont(new Font("SansSerif", Font.BOLD, 36));
            title.setForeground(Color.WHITE);
            title.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel tag = new JLabel("Smart Stock Analyzer");
            tag.setFont(new Font("SansSerif", Font.BOLD, 15));
            tag.setForeground(new Color(180, 195, 225));
            tag.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel desc = new JLabel("<html><br>Professional-grade<br>trading terminal.<br>"
                    + "Live markets, interactive charts,<br>portfolio analytics and more.</html>");
            desc.setFont(BODY);
            desc.setForeground(new Color(170, 185, 215));
            desc.setAlignmentX(Component.LEFT_ALIGNMENT);

            p.add(logo); p.add(Box.createVerticalStrut(20));
            p.add(title); p.add(Box.createVerticalStrut(6));
            p.add(tag); p.add(Box.createVerticalStrut(24));
            p.add(desc); p.add(Box.createVerticalGlue());

            JLabel v = new JLabel("v5.2");
            v.setFont(SMALL);
            v.setForeground(new Color(150, 165, 195));
            v.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(v);
            return p;
        }

        JPanel formPanel() {
            JPanel wrap = new JPanel(new GridBagLayout());
            wrap.setBackground(BG_PANEL);

            JPanel c = new JPanel();
            c.setBackground(BG_CARD);
            c.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    new EmptyBorder(24, 28, 24, 28)));
            c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
            c.setPreferredSize(new Dimension(340, 510));

            JLabel hd = h1("Welcome back");
            hd.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel sub = muted("Sign in to continue");
            sub.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.add(hd); c.add(Box.createVerticalStrut(4)); c.add(sub);
            c.add(Box.createVerticalStrut(20));

            JLabel rl = muted("SIGN IN AS");
            rl.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.add(rl); c.add(Box.createVerticalStrut(6));

            JPanel roleRow = new JPanel(new GridLayout(1, 2, 8, 0));
            roleRow.setOpaque(false);
            roleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            roleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            Pill userBtn = new Pill("USER");
            Pill adminBtn = new Pill("ADMIN");
            userBtn.setHorizontalAlignment(SwingConstants.CENTER);
            adminBtn.setHorizontalAlignment(SwingConstants.CENTER);
            userBtn.setSelected(true);
            ButtonGroup grp = new ButtonGroup();
            grp.add(userBtn); grp.add(adminBtn);
            userBtn.addActionListener(e -> selectedRole = "USER");
            adminBtn.addActionListener(e -> selectedRole = "ADMIN");

            roleRow.add(userBtn); roleRow.add(adminBtn);
            c.add(roleRow); c.add(Box.createVerticalStrut(18));

            JLabel ul = muted("EMAIL ADDRESS"); ul.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.add(ul); c.add(Box.createVerticalStrut(6));
            userField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            userField.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.add(userField); c.add(Box.createVerticalStrut(14));

            JLabel pl = muted("PASSWORD"); pl.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.add(pl); c.add(Box.createVerticalStrut(6));
            passField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            passField.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.add(passField); c.add(Box.createVerticalStrut(10));

            errorLabel.setFont(SMALL); errorLabel.setForeground(DANGER);
            errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.add(errorLabel); c.add(Box.createVerticalStrut(8));

            JButton loginBtn = btn("Sign In", ACCENT);
            loginBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            loginBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            loginBtn.addActionListener(e -> attemptLogin());
            c.add(loginBtn); c.add(Box.createVerticalStrut(8));

            JButton regBtn = btn("Create New Account", BG_INPUT);
            regBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            regBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            regBtn.addActionListener(e -> showRegister());
            c.add(regBtn); c.add(Box.createVerticalStrut(8));
            JButton backBtn = btn("\u2190  Back to Home", BG_CARD);
            backBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            backBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            backBtn.addActionListener(e -> { new WelcomeScreen().setVisible(true); dispose(); });
            c.add(backBtn);

            getRootPane().setDefaultButton(loginBtn);
            wrap.add(c);
            return wrap;
        }

        void attemptLogin() {
            String email = userField.getText().trim();
            String password = new String(passField.getPassword());
            if (email.isEmpty() || password.isEmpty()) {
                errorLabel.setForeground(DANGER);
                errorLabel.setText("Please enter your email and password."); return;
            }
            try {
                Map<String, Object> response = API.login(email, password);
                Map<String, Object> data = asMap(response.get("user"));
                String role = stringValue(data.get("role"));
                if (!role.equals(selectedRole)) {
                    API.clearSession();
                    errorLabel.setForeground(DANGER);
                    errorLabel.setText("This account does not have the selected role."); return;
                }
                User user = new User(longValue(data.get("user_id")), stringValue(data.get("email")), "",
                        stringValue(data.get("name")), role, doubleValue(data.get("cash")));
                loadBackendData(user);
                new MainScreen(user).setVisible(true);
                dispose();
            } catch (ApiException ex) {
                errorLabel.setForeground(DANGER);
                errorLabel.setText(ex.getMessage());
            } catch (Exception ex) {
                errorLabel.setForeground(DANGER);
                errorLabel.setText("Cannot reach backend. Start Flask on 127.0.0.1:5000.");
            }
        }

        void showRegister() {
            JTextField u = input(); JPasswordField p = passInput(); JTextField n = input();
            JPanel f = new JPanel(new GridLayout(0, 1, 6, 6));
            f.setBackground(BG_CARD);
            f.add(muted("Email Address")); f.add(u);
            f.add(muted("Password")); f.add(p);
            f.add(muted("Display Name")); f.add(n);
            int r = JOptionPane.showConfirmDialog(this, f, "Create Account",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return;
            String email = u.getText().trim(), pw = new String(p.getPassword()), name = n.getText().trim();
            if (email.isEmpty() || pw.length() < 6 || name.isEmpty()) {
                errorLabel.setForeground(DANGER);
                errorLabel.setText("Enter name, email and a password of at least 6 characters."); return;
            }
            try {
                API.post("/api/register", mapOf("email", email, "password", pw, "name", name));
                errorLabel.setForeground(SUCCESS);
                errorLabel.setText("Account created in the database. You can now sign in.");
            } catch (ApiException ex) {
                errorLabel.setForeground(DANGER); errorLabel.setText(ex.getMessage());
            } catch (Exception ex) {
                errorLabel.setForeground(DANGER); errorLabel.setText("Backend unavailable. Start Flask first.");
            }
        }
    }

    // ==================== MAIN SCREEN ====================
    static class MainScreen extends JFrame {
        User me;
        List<Holding> mine;
        Set<String> watchlist = new LinkedHashSet<>();

        JLabel status;
        DefaultTableModel marketM, watchM, portM, histM, userM;
        JTable marketT, watchT, portT, userT;
        JTabbedPane mainTabs;
        JComboBox<String> countryFilter;
        JTextField searchField;
        DonutChart donut;
        JComboBox<String> tradeCombo;
        javax.swing.Timer backendRefreshTimer;

        final ChartPanel marketChart = new ChartPanel();
        final ChartPanel tradeChart = new ChartPanel();
        final ChartPanel watchChart = new ChartPanel();

        CardLayout watchCardLayout;
        JPanel watchCardStack;

        MainScreen(User user) {
            super("SSA Pro - " + user.displayName);
            me = user;
            mine = portfolioOf(user.username);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(1360, 840);
            setLocationRelativeTo(null);
            setMinimumSize(new Dimension(1150, 700));

            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(BG_DEEP);
            root.add(topBar(), BorderLayout.NORTH);
            root.add(tabs(), BorderLayout.CENTER);
            root.add(statusBar(), BorderLayout.SOUTH);
            setContentPane(root);
            backendRefreshTimer = new javax.swing.Timer(5000, e -> refreshFromBackend());
            backendRefreshTimer.start();
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosing(java.awt.event.WindowEvent e) {
                    if (backendRefreshTimer != null) backendRefreshTimer.stop();
                }
            });
        }

        void refreshFromBackend() {
            try {
                loadBackendData(me);
                updateMetrics(); refreshMarket(); refreshPortfolio(); refreshHistory(); refreshWatch();
                int selected = tradeCombo == null ? -1 : tradeCombo.getSelectedIndex();
                if (selected >= 0 && selected < COMPANIES.size()) tradeChart.setCompany(COMPANIES.get(selected));
            } catch (Exception ex) {
                setStatus("Backend refresh failed. Check Flask/MySQL connection.", true);
            }
        }

        JPanel topBar() {
            JPanel bar = new JPanel(new BorderLayout());
            bar.setBackground(BG_PANEL);
            bar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                    new EmptyBorder(12, 20, 12, 20)));

            JPanel left = new JPanel();
            left.setOpaque(false);
            left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
            JLabel logo = new JLabel("\u25C8");
            logo.setFont(new Font("SansSerif", Font.BOLD, 24));
            logo.setForeground(ACCENT);
            left.add(logo); left.add(Box.createHorizontalStrut(10));

            JPanel tt = new JPanel();
            tt.setOpaque(false);
            tt.setLayout(new BoxLayout(tt, BoxLayout.Y_AXIS));
            JLabel t = new JLabel("SSA Pro"); t.setFont(H2); t.setForeground(TEXT);
            JLabel s = muted("Smart Stock Analyzer  ·  Global Markets");
            tt.add(t); tt.add(s);
            left.add(tt);

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            right.setOpaque(false);
            JLabel role = new JLabel(me.role);
            role.setFont(new Font("SansSerif", Font.BOLD, 10));
            role.setOpaque(true);
            role.setBorder(new EmptyBorder(5, 10, 5, 10));
            if ("ADMIN".equals(me.role)) {
                role.setBackground(new Color(85, 65, 25)); role.setForeground(WARNING);
            } else {
                role.setBackground(new Color(30, 55, 100)); role.setForeground(ACCENT);
            }
            JLabel nm = new JLabel(me.displayName); nm.setFont(H3); nm.setForeground(TEXT);

            JButton logout = btn("Logout", new Color(90, 48, 58));
            logout.addActionListener(e -> {
                try { API.post("/api/logout", mapOf()); } catch (Exception ignored) { }
                API.clearSession(); if (backendRefreshTimer != null) backendRefreshTimer.stop(); dispose(); new WelcomeScreen().setVisible(true);
            });

            right.add(role); right.add(nm); right.add(logout);
            bar.add(left, BorderLayout.WEST);
            bar.add(right, BorderLayout.EAST);
            return bar;
        }

        JTabbedPane tabs() {
            mainTabs = new JTabbedPane();
            mainTabs.setFont(H3);
            mainTabs.setOpaque(true);
            mainTabs.setBackground(BG_DEEP);
            mainTabs.setForeground(Color.WHITE);
            mainTabs.setBorder(new EmptyBorder(10, 14, 10, 14));

            mainTabs.setUI(new BasicTabbedPaneUI() {
                @Override protected void installDefaults() {
                    super.installDefaults();
                    tabInsets = new Insets(8, 12, 8, 12);
                    contentBorderInsets = new Insets(0, 0, 0, 0);
                    tabAreaInsets = new Insets(0, 0, 6, 0);
                }
                @Override protected void paintTabBackground(Graphics g, int tabPlacement,
                                                            int tabIndex, int x, int y, int w, int h,
                                                            boolean isSelected) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(isSelected ? BG_CARD : BG_DEEP);
                    g2.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 10, 10);
                    if (isSelected) {
                        g2.setColor(ACCENT);
                        g2.fillRoundRect(x + 10, y + h - 5, w - 20, 3, 3, 3);
                    }
                    g2.dispose();
                }
                @Override protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                                        int x, int y, int w, int h, boolean isSelected) {}
                @Override protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {}
                @Override protected void paintFocusIndicator(Graphics g, int tabPlacement,
                                                             Rectangle[] rects, int tabIndex,
                                                             Rectangle iconRect, Rectangle textRect,
                                                             boolean isSelected) {}
                @Override protected void paintText(Graphics g, int tabPlacement, Font font,
                                                   FontMetrics metrics, int tabIndex,
                                                   String title, Rectangle textRect,
                                                   boolean isSelected) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setFont(H3);
                    g.setColor(isSelected ? Color.WHITE : TEXT_MUTED);
                    g.drawString(title, textRect.x, textRect.y + metrics.getAscent());
                }
            });

            mainTabs.addTab("Dashboard", dashboardTab());
            mainTabs.addTab("Market",    marketTab());
            mainTabs.addTab("Trade",     tradeTab());
            mainTabs.addTab("Portfolio", portfolioTab());
            mainTabs.addTab("Watchlist", watchlistTab());
            mainTabs.addTab("History",   historyTab());
            mainTabs.addTab("Guide",     guideTab());
            if ("ADMIN".equals(me.role)) mainTabs.addTab("Admin", adminTab());

            mainTabs.addChangeListener(ev -> {
                int i = mainTabs.getSelectedIndex();
                if (i < 0) return;
                switch (i) {
                    case 0 -> updateMetrics();
                    case 3 -> refreshPortfolio();
                    case 4 -> refreshWatch();
                    case 5 -> refreshHistory();
                    default -> {}
                }
            });
            return mainTabs;
        }

        JPanel eastGap(JComponent main, JComponent east) {
            JPanel p = new JPanel(new BorderLayout(14, 0));
            p.setOpaque(false);
            p.add(main, BorderLayout.CENTER);
            p.add(east, BorderLayout.EAST);
            return p;
        }

        // -------- DASHBOARD --------
        final KpiCard kNet = new KpiCard("NET WORTH", ACCENT);
        final KpiCard kDay = new KpiCard("TODAY'S CHANGE", SUCCESS);
        final KpiCard kPl = new KpiCard("TOTAL PROFIT / LOSS", WARNING);
        final KpiCard kCash = new KpiCard("AVAILABLE CASH", new Color(190, 120, 255));
        final StockChart perfChart = new StockChart();
        final JLabel perfValueL = new JLabel(" "), perfChgL = new JLabel(" ");
        final JLabel perfNote = muted(" "), greetL = new JLabel(" "), dateL = muted(" ");
        int perfDays = 66;
        boolean showGainers = true;
        DefaultTableModel moversM, dashHoldM, dashActM;
        EmptyStack holdStack, actStack;
        final Pill[] guidePills = new Pill[6];

        JPanel gap(int h) {
            JPanel p = new JPanel();
            p.setOpaque(false);
            p.setPreferredSize(new Dimension(1, h));
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
            return p;
        }

        JPanel dashCard(String title, String subtitle, JComponent right, JComponent body) {
            JPanel card = new JPanel(new BorderLayout(0, 10));
            card.setBackground(BG_CARD);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    new EmptyBorder(14, 18, 14, 18)));
            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            JPanel tl = new JPanel();
            tl.setOpaque(false);
            tl.setLayout(new BoxLayout(tl, BoxLayout.Y_AXIS));
            JLabel t = h2(title);
            t.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel s = muted(subtitle);
            s.setAlignmentX(Component.LEFT_ALIGNMENT);
            tl.add(t); tl.add(Box.createVerticalStrut(2)); tl.add(s);
            head.add(tl, BorderLayout.WEST);
            if (right != null) head.add(right, BorderLayout.EAST);
            card.add(head, BorderLayout.NORTH);
            card.add(body, BorderLayout.CENTER);
            return card;
        }

        JScrollPane plainScroll(JComponent c) {
            JScrollPane sp = scroll(c);
            sp.setBorder(null);
            return sp;
        }

        JPanel emptyPanel(String msg, String btnText, Runnable act) {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(BG_CARD);
            JPanel in = new JPanel();
            in.setOpaque(false);
            in.setLayout(new BoxLayout(in, BoxLayout.Y_AXIS));
            JLabel l = new JLabel("<html><div style='text-align:center;color:#96a0b9;width:220px;'>" + msg + "</div></html>",
                    SwingConstants.CENTER);
            l.setFont(BODY);
            l.setAlignmentX(Component.CENTER_ALIGNMENT);
            in.add(l);
            if (btnText != null) {
                JButton b = btn(btnText, ACCENT);
                b.setAlignmentX(Component.CENTER_ALIGNMENT);
                b.addActionListener(e -> act.run());
                in.add(Box.createVerticalStrut(12));
                in.add(b);
            }
            p.add(in);
            return p;
        }

        void showGuidePage(int i) {
            mainTabs.setSelectedIndex(6);
            if (guidePills[i] != null) guidePills[i].doClick();
        }

        JPanel chip(String name, String code) {
            double sum = 0; int k = 0;
            for (Company c : COMPANIES) if (c.country.equals(code)) { sum += c.changePct; k++; }
            double avg = k == 0 ? 0 : sum / k;
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBackground(BG_CARD);
            p.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    new EmptyBorder(10, 14, 10, 14)));
            JLabel a = muted(name.toUpperCase() + "  \u00B7  " + k + " stocks");
            a.setFont(SMALL_B);
            JLabel b = new JLabel((avg >= 0 ? "\u25B2 " : "\u25BC ") + pct(avg));
            b.setFont(new Font("SansSerif", Font.BOLD, 17));
            b.setForeground(avg >= 0 ? SUCCESS : DANGER);
            a.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(a); p.add(Box.createVerticalStrut(3)); p.add(b);
            return p;
        }

        JScrollPane dashboardTab() {
            perfChart.valueOnly = true;
            perfChart.showMA = false;

            // header
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            JPanel hl = new JPanel();
            hl.setOpaque(false);
            hl.setLayout(new BoxLayout(hl, BoxLayout.Y_AXIS));
            greetL.setFont(new Font("SansSerif", Font.BOLD, 26));
            greetL.setForeground(TEXT);
            greetL.setAlignmentX(Component.LEFT_ALIGNMENT);
            dateL.setAlignmentX(Component.LEFT_ALIGNMENT);
            hl.add(greetL); hl.add(Box.createVerticalStrut(3)); hl.add(dateL);
            JPanel hr = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            hr.setOpaque(false);
            JLabel live = new JLabel("\u25CF  Simulated live");
            live.setFont(SMALL_B);
            live.setForeground(SUCCESS);
            live.setBorder(new EmptyBorder(0, 0, 0, 8));
            JButton bFind = btn("Find Stocks", BG_INPUT);
            bFind.addActionListener(e -> showGuidePage(1));
            JButton bMarket = btn("Explore Market", BG_INPUT);
            bMarket.addActionListener(e -> mainTabs.setSelectedIndex(1));
            JButton bTrade = btn("+  New Trade", ACCENT);
            bTrade.addActionListener(e -> mainTabs.setSelectedIndex(2));
            hr.add(live); hr.add(bFind); hr.add(bMarket); hr.add(bTrade);
            header.add(hl, BorderLayout.WEST);
            header.add(hr, BorderLayout.EAST);

            TickerTape ticker = new TickerTape(false);
            ticker.setPreferredSize(new Dimension(100, 34));
            ticker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

            // KPI row
            JPanel kpis = new JPanel(new GridLayout(1, 4, 12, 0));
            kpis.setOpaque(false);
            kpis.setPreferredSize(new Dimension(100, 118));
            kpis.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));
            kpis.add(kNet); kpis.add(kDay); kpis.add(kPl); kpis.add(kCash);

            // performance + allocation
            JPanel pills = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            pills.setOpaque(false);
            ButtonGroup pg = new ButtonGroup();
            String[] pn = {"1M", "3M", "6M", "1Y"};
            int[] pd = {22, 66, 126, 252};
            for (int i = 0; i < pn.length; i++) {
                Pill p = new Pill(pn[i]);
                final int d = pd[i];
                p.setSelected(d == perfDays);
                p.addActionListener(e -> { perfDays = d; refreshPerf(); });
                pg.add(p); pills.add(p);
            }
            perfValueL.setFont(new Font("SansSerif", Font.BOLD, 26));
            perfValueL.setForeground(TEXT);
            perfChgL.setFont(H3);
            JPanel vals = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
            vals.setOpaque(false);
            vals.add(perfValueL); vals.add(perfChgL);
            JPanel perfBody = new JPanel(new BorderLayout(0, 6));
            perfBody.setOpaque(false);
            perfBody.add(vals, BorderLayout.NORTH);
            perfBody.add(perfChart, BorderLayout.CENTER);
            perfBody.add(perfNote, BorderLayout.SOUTH);
            JPanel perfCard = dashCard("Portfolio Performance", "Your current holdings plus cash, replayed over time", pills, perfBody);

            donut = new DonutChart(mine);
            JPanel donutCard = dashCard("Allocation", "Where your invested money sits", null, donut);

            JPanel mid = new JPanel(new GridBagLayout());
            mid.setOpaque(false);
            mid.setPreferredSize(new Dimension(100, 350));
            mid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 350));
            GridBagConstraints gc = new GridBagConstraints();
            gc.fill = GridBagConstraints.BOTH;
            gc.weighty = 1;
            gc.gridx = 0; gc.weightx = 0.66; gc.insets = new Insets(0, 0, 0, 12);
            mid.add(perfCard, gc);
            gc.gridx = 1; gc.weightx = 0.34; gc.insets = new Insets(0, 0, 0, 0);
            mid.add(donutCard, gc);

            // country chips
            JPanel chips = new JPanel(new GridLayout(1, 6, 10, 0));
            chips.setOpaque(false);
            chips.setPreferredSize(new Dimension(100, 66));
            chips.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
            String[][] ct = {{"India", "IN"}, {"USA", "US"}, {"UK", "UK"}, {"Japan", "JP"}, {"Germany", "DE"}, {"China", "CN"}};
            for (String[] c : ct) chips.add(chip(c[0], c[1]));

            // movers
            moversM = new DefaultTableModel(new Object[]{"Symbol", "Price", "Change"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            JTable mt = darkTable(moversM);
            mt.setRowHeight(30);
            mt.getColumnModel().getColumn(1).setCellRenderer(rightR());
            mt.getColumnModel().getColumn(2).setCellRenderer(colorR());
            JPanel mp = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            mp.setOpaque(false);
            ButtonGroup mg = new ButtonGroup();
            Pill pg1 = new Pill("Gainers"), pl1 = new Pill("Losers");
            pg1.setSelected(true);
            pg1.addActionListener(e -> { showGainers = true; refreshMovers(); });
            pl1.addActionListener(e -> { showGainers = false; refreshMovers(); });
            mg.add(pg1); mg.add(pl1); mp.add(pg1); mp.add(pl1);
            JPanel moversCard = dashCard("Market Movers", "Biggest changes today", mp, plainScroll(mt));

            // holdings
            dashHoldM = new DefaultTableModel(new Object[]{"Symbol", "Qty", "Value", "P&L"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            JTable ht = darkTable(dashHoldM);
            ht.setRowHeight(30);
            ht.getColumnModel().getColumn(1).setCellRenderer(rightR());
            ht.getColumnModel().getColumn(2).setCellRenderer(rightR());
            ht.getColumnModel().getColumn(3).setCellRenderer(colorR());
            holdStack = new EmptyStack(plainScroll(ht),
                    emptyPanel("You don't own any stocks yet.<br>Buy your first one to see it here.", "Make First Trade",
                            () -> mainTabs.setSelectedIndex(2)));
            JPanel holdCard = dashCard("Your Holdings", "Top positions by value", null, holdStack);

            // activity
            dashActM = new DefaultTableModel(new Object[]{"Time", "Type", "Symbol", "Total"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            JTable at = darkTable(dashActM);
            at.setRowHeight(30);
            at.getColumnModel().getColumn(1).setCellRenderer(new TypeR());
            at.getColumnModel().getColumn(3).setCellRenderer(rightR());
            actStack = new EmptyStack(plainScroll(at),
                    emptyPanel("No trades yet.<br>Your latest orders will appear here.", null, null));
            JPanel actCard = dashCard("Recent Activity", "Your latest trades", null, actStack);

            JPanel bottom = new JPanel(new GridLayout(1, 3, 12, 0));
            bottom.setOpaque(false);
            bottom.setPreferredSize(new Dimension(100, 300));
            bottom.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
            bottom.add(moversCard); bottom.add(holdCard); bottom.add(actCard);

            JScrollPane page = pageOf(gap(6), header, gap(14), ticker, gap(14), kpis, gap(14), mid, gap(14), chips, gap(14), bottom, gap(8));
            page.setBorder(null);
            updateMetrics();
            return page;
        }

        double[] netWorthSeries(boolean holdingsOnly) {
            List<Candle> ref = history(COMPANIES.get(0));
            int n = ref.size();
            double[] s = new double[n];
            for (int i = 0; i < n; i++) {
                double v = holdingsOnly ? 0 : me.cash;
                for (Holding h : mine) {
                    Company c = findCompany(h.symbol);
                    v += h.qty * (c != null ? history(c).get(i).c : h.avgPrice);
                }
                s[i] = v;
            }
            return s;
        }

        void refreshMovers() {
            if (moversM == null) return;
            moversM.setRowCount(0);
            COMPANIES.stream()
                    .filter(c -> showGainers ? c.changePct > 0 : c.changePct < 0)
                    .sorted((a, b) -> showGainers ? Double.compare(b.changePct, a.changePct)
                                                  : Double.compare(a.changePct, b.changePct))
                    .limit(7)
                    .forEach(c -> moversM.addRow(new Object[]{c.symbol, inr(c.price), pct(c.changePct)}));
        }

        void refreshPerf() {
            double[] ns = netWorthSeries(false);
            List<Candle> ref = history(COMPANIES.get(0));
            int n = ref.size(), from = Math.max(0, n - perfDays);
            List<Candle> pc = new ArrayList<>();
            for (int i = from; i < n; i++) {
                Candle c = new Candle();
                c.date = ref.get(i).date;
                c.o = c.h = c.l = c.c = ns[i];
                pc.add(c);
            }
            perfChart.setData(pc);
            double f = ns[from], l = ns[n - 1], ch = l - f;
            String range = perfDays == 22 ? "1 month" : perfDays == 66 ? "3 months" : perfDays == 126 ? "6 months" : "1 year";
            perfValueL.setText(inr0(l));
            perfChgL.setText((ch >= 0 ? "\u25B2 " : "\u25BC ") + signedInr0(ch) + "  (" + pct(f > 0 ? (l / f - 1) * 100 : 0) + ")  over " + range);
            perfChgL.setForeground(colorFor(ch));
            perfNote.setText(mine.isEmpty()
                    ? "Make your first trade and your portfolio's movement will show up here. Until then the line is flat at your cash balance."
                    : "Simulated prices. Hover the chart to see your net worth on any day.");
        }

        void updateMetrics() {
            if (dashActM == null) return;
            int hr = LocalTime.now().getHour();
            greetL.setText((hr < 12 ? "Good morning" : hr < 17 ? "Good afternoon" : "Good evening") + ", " + me.displayName.split(" ")[0]);
            dateL.setText(java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"))
                    + "   \u00B7   Your portfolio at a glance");

            double inv = invested(mine), val = totalValue(mine), pl = val - inv, net = me.cash + val;
            double start = "ADMIN".equals(me.role) ? 1000000 : 100000;
            double day = 0;
            for (Holding h : mine) {
                Company c = findCompany(h.symbol);
                if (c != null) day += h.qty * (c.price - c.price / (1 + c.changePct / 100.0));
            }
            double prevNet = net - day;
            double dayPct = prevNet > 0 ? day / prevNet * 100 : 0;
            double ret = inv > 0 ? pl / inv * 100 : 0;
            double[] ns = netWorthSeries(false), hv = netWorthSeries(true);
            boolean has = !mine.isEmpty();
            double[] plSeries = new double[hv.length];
            for (int i = 0; i < hv.length; i++) plSeries[i] = hv[i] - inv;

            kNet.set(inr0(net), TEXT, pct((net / start - 1) * 100) + "  since you started", colorFor(net - start),
                    has ? tail(ns, 60) : null, colorFor(net - start) == TEXT_MUTED ? ACCENT : colorFor(net - start), -1);
            kDay.set(signedInr0(day), has ? colorFor(day) : TEXT, has ? pct(dayPct) + "  vs yesterday" : "No holdings yet",
                    has ? colorFor(day) : TEXT_MUTED, has ? tail(ns, 20) : null, colorFor(day), -1);
            kPl.set(signedInr0(pl), has ? colorFor(pl) : TEXT, has ? pct(ret) + "  return on " + inr0(inv) : "Make your first trade to start",
                    has ? colorFor(pl) : TEXT_MUTED, has ? tail(plSeries, 60) : null, colorFor(pl), -1);
            kCash.set(inr0(me.cash), TEXT, String.format("%.0f%% of net worth  \u00B7  Invested %s", net > 0 ? me.cash / net * 100 : 0, inr0(inv)),
                    TEXT_MUTED, null, ACCENT, net > 0 ? me.cash / net : 1);

            refreshPerf();
            refreshMovers();

            dashHoldM.setRowCount(0);
            List<Holding> hs = new ArrayList<>(mine);
            hs.sort((a, b) -> Double.compare(holdingValue(b), holdingValue(a)));
            for (int i = 0; i < Math.min(6, hs.size()); i++) {
                Holding h = hs.get(i);
                double v = holdingValue(h);
                double holdingPL = Double.isNaN(h.backendProfitLoss) ? v - h.qty * h.avgPrice : h.backendProfitLoss;
                dashHoldM.addRow(new Object[]{h.symbol, h.qty, inr0(v), signedInr0(holdingPL)});
            }
            holdStack.showEmpty(hs.isEmpty());

            dashActM.setRowCount(0);
            int shown = 0;
            for (int i = HISTORY.size() - 1; i >= 0 && shown < 6; i--) {
                String[] h = HISTORY.get(i);
                if (h.length > 6 && h[6].equals(me.username)) {
                    dashActM.addRow(new Object[]{h[0], h[1], h[2], h[5].replaceAll("\\.\\d\\d$", "")});
                    shown++;
                }
            }
            actStack.showEmpty(shown == 0);
            if (donut != null) donut.repaint();
        }

        // -------- MARKET --------
        JPanel marketTab() {
            JPanel p = new JPanel(new BorderLayout(10, 12));
            p.setBackground(BG_DEEP);
            p.setBorder(new EmptyBorder(14, 6, 6, 6));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(h1("Market Watch  ·  Global"), BorderLayout.WEST);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            actions.setOpaque(false);

            searchField = input();
            searchField.setPreferredSize(new Dimension(160, 34));
            countryFilter = new JComboBox<>(
                    new String[]{"All Countries", "India", "USA", "UK", "Japan", "Germany", "China"});
            styleCombo(countryFilter);
            countryFilter.setPreferredSize(new Dimension(140, 34));

            JButton add = btn("★  Add to Watchlist", ACCENT);
            add.addActionListener(e -> addSelectedToWatchlist());

            actions.add(muted("Search:")); actions.add(searchField);
            actions.add(muted("Filter:")); actions.add(countryFilter);
            actions.add(add);
            top.add(actions, BorderLayout.EAST);
            p.add(top, BorderLayout.NORTH);

            marketM = new DefaultTableModel(
                    new Object[]{"Symbol", "Company", "Sector", "Country", "Price", "Change %"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            marketT = darkTable(marketM);
            marketT.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            marketT.getColumnModel().getColumn(4).setCellRenderer(rightR());
            marketT.getColumnModel().getColumn(5).setCellRenderer(colorR());
            marketT.getColumnModel().getColumn(0).setPreferredWidth(95);
            marketT.getColumnModel().getColumn(1).setPreferredWidth(160);
            marketT.getColumnModel().getColumn(3).setPreferredWidth(100);
            marketT.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                int r = marketT.getSelectedRow();
                if (r >= 0) marketChart.setCompany(findCompany((String) marketM.getValueAt(r, 0)));
            });
            countryFilter.addActionListener(e -> refreshMarket());
            onChange(searchField, this::refreshMarket);
            refreshMarket();

            p.add(eastGap(scroll(marketT), marketChart), BorderLayout.CENTER);
            return p;
        }

        void refreshMarket() {
            if (marketM == null) return;
            String filter = (String) countryFilter.getSelectedItem();
            String q = searchField.getText().trim().toLowerCase();
            int sel = marketT.getSelectedRow();
            String keep = sel >= 0 ? (String) marketM.getValueAt(sel, 0) : null;

            marketM.setRowCount(0);
            int keepRow = -1;
            for (Company c : COMPANIES) {
                boolean okCountry = filter == null || filter.equals("All Countries")
                        || countryFlag(c.country).contains(filter);
                boolean okSearch = q.isEmpty() || c.symbol.toLowerCase().contains(q)
                        || c.name.toLowerCase().contains(q) || c.sector.toLowerCase().contains(q);
                if (okCountry && okSearch) {
                    if (c.symbol.equals(keep)) keepRow = marketM.getRowCount();
                    marketM.addRow(new Object[]{
                            c.symbol, c.name, c.sector,
                            countryFlag(c.country), inr(c.price), pct(c.changePct)});
                }
            }
            if (marketM.getRowCount() > 0) {
                int r = keepRow >= 0 ? keepRow : 0;
                marketT.setRowSelectionInterval(r, r);
            } else {
                marketChart.setCompany(null);
            }
        }

        void addSelectedToWatchlist() {
            int r = marketT.getSelectedRow();
            if (r == -1) { setStatus("Select a company first.", true); return; }
            String sym = (String) marketM.getValueAt(r, 0);
            if (watchlist.add(sym)) {
                refreshWatch();
                setStatus(sym + " added to watchlist.", false);
            } else {
                setStatus(sym + " is already in your watchlist.", true);
            }
        }

        // -------- TRADE --------
        JPanel tradeTab() {
            JPanel page = new JPanel(new BorderLayout(14, 0));
            page.setBackground(BG_DEEP);
            page.setBorder(new EmptyBorder(14, 6, 6, 6));

            JPanel c = new JPanel(new GridBagLayout());
            c.setBackground(BG_CARD);
            c.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    new EmptyBorder(20, 24, 20, 24)));

            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(7, 7, 7, 7);
            g.fill = GridBagConstraints.HORIZONTAL;

            JLabel hd = h2("Place Market Order");
            g.gridx = 0; g.gridy = 0; g.gridwidth = 2;
            g.anchor = GridBagConstraints.WEST;
            c.add(hd, g);
            g.gridwidth = 1;

            JComboBox<String> combo = new JComboBox<>();
            for (Company cmp : COMPANIES)
                combo.addItem(cmp.symbol + "  -  " + cmp.name + "  (" + cmp.country + ")");
            styleCombo(combo);
            tradeCombo = combo;
            combo.setPreferredSize(new Dimension(300, 34));

            JSpinner qty = new JSpinner(new SpinnerNumberModel(1, 1, 1000000, 1));
            JSpinner.DefaultEditor ed = (JSpinner.DefaultEditor) qty.getEditor();
            ed.getTextField().setBackground(BG_INPUT);
            ed.getTextField().setForeground(TEXT);
            ed.getTextField().setCaretColor(TEXT);
            ed.getTextField().setFont(BODY);

            Pill buy = new Pill("BUY");
            Pill sell = new Pill("SELL");
            buy.setSelected(true);
            ButtonGroup bg = new ButtonGroup();
            bg.add(buy); bg.add(sell);

            JLabel priceL = new JLabel("Rs.0.00");
            JLabel ownedL = new JLabel("0");
            JLabel totalL = new JLabel("Rs.0.00");
            JLabel cashAfterL = new JLabel("Rs.0.00");
            priceL.setFont(new Font("SansSerif", Font.BOLD, 16)); priceL.setForeground(ACCENT);
            ownedL.setFont(H3); ownedL.setForeground(TEXT);
            totalL.setFont(new Font("SansSerif", Font.BOLD, 16)); totalL.setForeground(ACCENT);
            cashAfterL.setFont(new Font("SansSerif", Font.BOLD, 14)); cashAfterL.setForeground(SUCCESS);

            Runnable update = () -> {
                int idx = combo.getSelectedIndex();
                if (idx < 0) return;
                Company cmp = COMPANIES.get(idx);
                int q = (Integer) qty.getValue();
                double gross = cmp.price * q;
                double total = buy.isSelected() ? gross * 1.001 : gross * 0.999; // backend fee is 0.1%
                Holding h = findHolding(mine, cmp.symbol);
                priceL.setText(inr(cmp.price));
                ownedL.setText(h == null ? "0" : String.valueOf(h.qty));
                totalL.setText(inr(total));
                double after = buy.isSelected() ? me.cash - total : me.cash + total;
                cashAfterL.setText(inr(after));
                cashAfterL.setForeground(after < 0 ? DANGER : SUCCESS);
                tradeChart.setCompany(cmp);
            };
            combo.addActionListener(e -> update.run());
            qty.addChangeListener(e -> update.run());
            buy.addActionListener(e -> update.run());
            sell.addActionListener(e -> update.run());
            update.run();

            int row = 1;
            row = addRow(c, g, row, "Company", combo);
            row = addRow(c, g, row, "Current Price", priceL);
            row = addRow(c, g, row, "Shares Owned", ownedL);
            row = addRow(c, g, row, "Quantity", qty);

            g.gridx = 0; g.gridy = row; g.weightx = 0;
            c.add(muted("Order Type"), g);
            JPanel radios = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            radios.setOpaque(false);
            radios.add(buy); radios.add(sell);
            g.gridx = 1; g.weightx = 1;
            c.add(radios, g);
            row++;

            row = addRow(c, g, row, "Estimated Total", totalL);
            row = addRow(c, g, row, "Cash After Trade", cashAfterL);

            JButton submit = btn("Submit Order", SUCCESS);
            g.gridx = 0; g.gridy = row; g.gridwidth = 2;
            g.fill = GridBagConstraints.NONE;
            g.anchor = GridBagConstraints.EAST;
            c.add(submit, g);

            submit.addActionListener(e -> {
                int idx = combo.getSelectedIndex();
                if (idx < 0) return;
                Company cmp = COMPANIES.get(idx);
                int q = (Integer) qty.getValue();
                boolean isBuy = buy.isSelected();
                String side = isBuy ? "BUY" : "SELL";
                try {
                    Map<String, Object> result;
                    try {
                        result = API.post("/api/trade", mapOf("symbol", cmp.symbol, "type", side,
                                "quantity", q, "confirm", false));
                    } catch (ApiException ex) {
                        if (ex.statusCode == 409 && Boolean.TRUE.equals(ex.payload.get("needs_confirmation"))) {
                            int choice = JOptionPane.showConfirmDialog(MainScreen.this,
                                    ex.getMessage() + "\n\nDo you want to explicitly confirm this trade?",
                                    "High-risk trade confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                            if (choice != JOptionPane.YES_OPTION) return;
                            result = API.post("/api/trade", mapOf("symbol", cmp.symbol, "type", side,
                                    "quantity", q, "confirm", true));
                        } else throw ex;
                    }
                    loadBackendData(me);
                    refreshMarket(); refreshPortfolio(); refreshHistory(); refreshWatch(); updateMetrics();
                    update.run();
                    setStatus(stringValue(result.get("message")) + "  ·  Total "
                            + inr(doubleValue(result.get("total"))) + "  ·  Cash " + inr(me.cash), false);
                } catch (ApiException ex) {
                    setStatus(ex.getMessage(), true);
                } catch (Exception ex) {
                    setStatus("Backend connection failed. Check that Flask is running.", true);
                }
            });

            JPanel formWrap = new JPanel(new GridBagLayout());
            formWrap.setOpaque(false);
            GridBagConstraints fg = new GridBagConstraints();
            fg.anchor = GridBagConstraints.NORTH;
            formWrap.add(c, fg);

            page.add(formWrap, BorderLayout.WEST);
            page.add(tradeChart, BorderLayout.CENTER);
            return page;
        }

        int addRow(JPanel panel, GridBagConstraints g, int row, String label, JComponent field) {
            g.gridx = 0; g.gridy = row; g.weightx = 0;
            panel.add(muted(label), g);
            g.gridx = 1; g.weightx = 1;
            panel.add(field, g);
            return row + 1;
        }

        // -------- PORTFOLIO --------
        JPanel portfolioTab() {
            JPanel p = new JPanel(new BorderLayout(10, 12));
            p.setBackground(BG_DEEP);
            p.setBorder(new EmptyBorder(14, 6, 6, 6));
            p.add(h1("My Portfolio"), BorderLayout.NORTH);

            portM = new DefaultTableModel(
                    new Object[]{"Symbol", "Company", "Qty", "Avg Price", "Current", "Value", "P&L"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            portT = darkTable(portM);
            for (int i = 2; i < 6; i++) portT.getColumnModel().getColumn(i).setCellRenderer(rightR());
            portT.getColumnModel().getColumn(6).setCellRenderer(colorR());
            refreshPortfolio();
            p.add(scroll(portT), BorderLayout.CENTER);
            return p;
        }

        void refreshPortfolio() {
            if (portM == null) return;
            portM.setRowCount(0);
            for (Holding h : mine) {
                Company c = findCompany(h.symbol);
                double cur = c != null ? c.price : h.avgPrice;
                double inv = h.qty * h.avgPrice;
                double val = h.qty * cur;
                double pl = Double.isNaN(h.backendProfitLoss) ? val - inv : h.backendProfitLoss;
                portM.addRow(new Object[]{
                        h.symbol,
                        c != null ? c.name : h.symbol,
                        h.qty,
                        inr(h.avgPrice),
                        inr(cur),
                        inr(val),
                        signedInr(pl)});
            }
        }

        // -------- WATCHLIST --------
        JPanel watchlistTab() {
            JPanel p = new JPanel(new BorderLayout(10, 12));
            p.setBackground(BG_DEEP);
            p.setBorder(new EmptyBorder(14, 6, 6, 6));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);

            JPanel titles = new JPanel();
            titles.setOpaque(false);
            titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
            JLabel sub = muted("Companies you're tracking  ·  add them from the Market tab");
            titles.add(h1("My Watchlist"));
            titles.add(Box.createVerticalStrut(4));
            titles.add(sub);
            top.add(titles, BorderLayout.WEST);

            JButton rm = btn("Remove Selected", DANGER);
            rm.addActionListener(e -> {
                int r = watchT.getSelectedRow();
                if (r == -1) { setStatus("Select a row first.", true); return; }
                String sym = (String) watchM.getValueAt(r, 0);
                watchlist.remove(sym);
                refreshWatch();
                setStatus(sym + " removed from watchlist.", false);
            });
            JPanel rmWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            rmWrap.setOpaque(false);
            rmWrap.add(rm);
            top.add(rmWrap, BorderLayout.EAST);
            p.add(top, BorderLayout.NORTH);

            watchM = new DefaultTableModel(
                    new Object[]{"Symbol", "Company", "Sector", "Country", "Price", "Change %"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            watchT = darkTable(watchM);
            watchT.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            watchT.getColumnModel().getColumn(4).setCellRenderer(rightR());
            watchT.getColumnModel().getColumn(5).setCellRenderer(colorR());
            watchT.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                int r = watchT.getSelectedRow();
                if (r >= 0) watchChart.setCompany(findCompany((String) watchM.getValueAt(r, 0)));
            });

            JLabel emptyHint = new JLabel(
                    "<html><div style='text-align:center;color:#96a0b9;'>"
                    + "<span style='font-size:30pt;color:#639bff;'>★</span><br><br>"
                    + "<span style='font-size:16pt;color:#f0f4fc;'>Your watchlist is empty</span><br><br>"
                    + "<span style='font-size:11pt;'>Go to the <b style='color:#639bff;'>Market</b> tab, "
                    + "select a company, then click <b style='color:#639bff;'>Add to Watchlist</b>."
                    + "</span></div></html>", SwingConstants.CENTER);
            emptyHint.setFont(BODY);

            JPanel emptyCard = new JPanel(new BorderLayout());
            emptyCard.setBackground(BG_CARD);
            emptyCard.setBorder(BorderFactory.createLineBorder(BORDER));
            emptyCard.add(emptyHint, BorderLayout.CENTER);

            JPanel tableCard = eastGap(scroll(watchT), watchChart);

            watchCardLayout = new CardLayout();
            watchCardStack = new JPanel(watchCardLayout);
            watchCardStack.setOpaque(false);
            watchCardStack.add(emptyCard, "EMPTY");
            watchCardStack.add(tableCard, "TABLE");

            refreshWatch();
            p.add(watchCardStack, BorderLayout.CENTER);
            return p;
        }

        void refreshWatch() {
            if (watchM == null) return;
            int sel = watchT.getSelectedRow();
            String keep = sel >= 0 ? (String) watchM.getValueAt(sel, 0) : null;
            watchM.setRowCount(0);
            int keepRow = -1;
            for (String sym : watchlist) {
                Company c = findCompany(sym);
                if (c != null) {
                    if (sym.equals(keep)) keepRow = watchM.getRowCount();
                    watchM.addRow(new Object[]{
                            c.symbol, c.name, c.sector,
                            countryFlag(c.country), inr(c.price), pct(c.changePct)});
                }
            }
            if (watchM.getRowCount() > 0) {
                int r = keepRow >= 0 ? keepRow : 0;
                watchT.setRowSelectionInterval(r, r);
            } else {
                watchChart.setCompany(null);
            }
            if (watchCardLayout != null && watchCardStack != null) {
                watchCardLayout.show(watchCardStack, watchlist.isEmpty() ? "EMPTY" : "TABLE");
            }
        }

        // -------- HISTORY --------
        JPanel historyTab() {
            JPanel p = new JPanel(new BorderLayout(10, 12));
            p.setBackground(BG_DEEP);
            p.setBorder(new EmptyBorder(14, 6, 6, 6));
            p.add(h1("Transaction History"), BorderLayout.NORTH);
            histM = new DefaultTableModel(
                    new Object[]{"Time", "Type", "Symbol", "Qty", "Price", "Total"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            JTable t = darkTable(histM);
            for (int i = 3; i < 6; i++) t.getColumnModel().getColumn(i).setCellRenderer(rightR());
            t.getColumnModel().getColumn(1).setCellRenderer(new CellR(SwingConstants.LEFT, false) {
                @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean s,
                                                                          boolean f, int r, int c) {
                    super.getTableCellRendererComponent(tb, v, s, f, r, c);
                    if (!s) setForeground("BUY".equals(v) ? SUCCESS : DANGER);
                    setFont(H3);
                    return this;
                }
            });
            refreshHistory();
            p.add(scroll(t), BorderLayout.CENTER);
            return p;
        }

        void refreshHistory() {
            if (histM == null) return;
            histM.setRowCount(0);
            for (int i = HISTORY.size() - 1; i >= 0; i--) {
                String[] h = HISTORY.get(i);
                if (h.length > 6 && h[6].equals(me.username)) histM.addRow(h);
            }
        }

        // -------- GUIDE --------
        JComboBox<String> profileBox, regionBox;
        JSpinner budgetSpin;
        DefaultTableModel finderM;
        JTable finderT, compT;
        JLabel whyL, planL, profileDesc;
        final List<Pick> picks = new ArrayList<>();
        List<Pick> planPicks = new ArrayList<>();
        final ChartPanel guideChart = new ChartPanel();

        JPanel guideTab() {
            JPanel outer = new JPanel(new BorderLayout(0, 12));
            outer.setBackground(BG_DEEP);
            outer.setBorder(new EmptyBorder(14, 6, 6, 6));

            JPanel titleRow = new JPanel(new BorderLayout());
            titleRow.setOpaque(false);
            titleRow.add(h1("Investing Guide"), BorderLayout.WEST);
            titleRow.add(muted("New here?  Read 'Start Here', then try the Stock Finder."), BorderLayout.EAST);

            CardLayout cl = new CardLayout();
            JPanel cards = new JPanel(cl);
            cards.setOpaque(false);
            String[] names = {"Start Here", "Stock Finder", "How to Choose", "Companies", "Read Charts", "Glossary"};
            JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            nav.setOpaque(false);
            ButtonGroup ng = new ButtonGroup();
            for (int i = 0; i < names.length; i++) {
                final String nm = names[i];
                Pill pl = new Pill(nm);
                pl.setSelected(i == 0);
                pl.addActionListener(e -> cl.show(cards, nm));
                guidePills[i] = pl; ng.add(pl); nav.add(pl);
            }
            JPanel head = new JPanel();
            head.setOpaque(false);
            head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
            titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            nav.setAlignmentX(Component.LEFT_ALIGNMENT);
            head.add(titleRow); head.add(Box.createVerticalStrut(10)); head.add(nav);

            cards.add(startPage(), names[0]);
            cards.add(finderPage(), names[1]);
            cards.add(choosePage(), names[2]);
            cards.add(companiesPage(), names[3]);
            cards.add(chartsPage(), names[4]);
            cards.add(glossaryPage(), names[5]);

            outer.add(head, BorderLayout.NORTH);
            outer.add(cards, BorderLayout.CENTER);
            return outer;
        }

        JScrollPane pageOf(JComponent... items) {
            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBackground(BG_DEEP);
            for (JComponent c : items) { c.setAlignmentX(Component.LEFT_ALIGNMENT); content.add(c); }
            JPanel top = new JPanel(new BorderLayout());
            top.setBackground(BG_DEEP);
            top.add(content, BorderLayout.NORTH);
            JScrollPane sp = scroll(top);
            sp.getViewport().setBackground(BG_DEEP);
            return sp;
        }

        JPanel guideCard(String title, String body) { return guideCard(title, body, ACCENT); }

        JPanel guideCard(String title, String body, Color accent) {
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(BG_CARD);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(BORDER),
                            new EmptyBorder(16, 20, 16, 20))));
            JLabel t = new JLabel(title);
            t.setFont(new Font("SansSerif", Font.BOLD, 15));
            t.setForeground(accent);
            t.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel b = new JLabel("<html><div style='width:860px;color:#c8cee0;'>" + body + "</div></html>");
            b.setFont(BODY);
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(t); card.add(Box.createVerticalStrut(8)); card.add(b);

            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.setBackground(BG_DEEP);
            wrapper.setBorder(new EmptyBorder(0, 0, 12, 0));
            wrapper.add(card, BorderLayout.CENTER);
            return wrapper;
        }

        String table(String[][] rows, int firstColWidth) {
            StringBuilder sb = new StringBuilder("<table cellpadding='5' cellspacing='0'>");
            for (String[] r : rows) {
                sb.append("<tr><td width='").append(firstColWidth).append("' valign='top'><b><font color='#639bff'>")
                  .append(r[0]).append("</font></b></td><td valign='top'>").append(r[1]).append("</td></tr>");
            }
            return sb.append("</table>").toString();
        }

        // ---- Page 1: Start Here ----
        JScrollPane startPage() {
            return pageOf(
                guideCard("Welcome  ·  this is a safe practice account",
                    "You start with <b>Rs.1,00,000 of virtual cash</b>. Nothing here uses real money, so the best way to "
                    + "learn is to <b>try things</b>. A good investor follows the same loop every time:<br><br>"
                    + "<b>Research</b> a company &rarr; <b>check the chart</b> &rarr; <b>decide how much</b> &rarr; "
                    + "<b>trade</b> &rarr; <b>review</b> what happened."),
                guideCard("Your first investment in 5 steps",
                    "<b>1.</b> Open the <b>Stock Finder</b> page (next to 'Start Here' above). Pick your style. "
                    + "Not sure? Choose <b>Balanced</b>.<br>"
                    + "<b>2.</b> Enter how much you want to invest. The finder ranks companies and shows a suggested split.<br>"
                    + "<b>3.</b> Click a company in the list to see its chart and why it was picked.<br>"
                    + "<b>4.</b> Click <b>Open Selected in Trade</b>, enter a small quantity (try 10-20% of your cash) and press "
                    + "<b>Submit Order</b>.<br>"
                    + "<b>5.</b> Check the <b>Portfolio</b> and <b>Dashboard</b> tabs. Come back later, compare, and learn."),
                guideCard("What each tab does",
                    table(new String[][]{
                        {"Dashboard", "Your cash, invested amount, profit/loss, return % and how your money is spread out (Allocation)."},
                        {"Market", "All companies with search and country filter. Click one to see its chart."},
                        {"Trade", "Buy or sell. Shows cost, shares you own and cash left after the trade."},
                        {"Portfolio", "Everything you own, your average buy price and profit/loss per stock."},
                        {"Watchlist", "A shortlist of companies you are following but have not bought."},
                        {"History", "A log of every trade, newest first."}}, 110)),
                guideCard("Important: this is a simulator",
                    "Prices and chart history in this app are <b>simulated for learning</b>. The Stock Finder's picks "
                    + "show how to <i>think</i> about choosing stocks. They are <b>not financial advice</b> and not a "
                    + "prediction of real prices. For real investing, research each company properly and use a "
                    + "regulated broker.", WARNING));
        }

        // ---- Page 2: Stock Finder ----
        JScrollPane finderPage() {
            profileBox = new JComboBox<>(new String[]{"Conservative", "Balanced", "Aggressive"});
            profileBox.setSelectedItem("Balanced");
            styleCombo(profileBox);
            profileBox.setPreferredSize(new Dimension(150, 34));
            regionBox = new JComboBox<>(new String[]{"All Countries", "India", "USA", "UK", "Japan", "Germany", "China"});
            styleCombo(regionBox);
            regionBox.setPreferredSize(new Dimension(150, 34));
            budgetSpin = new JSpinner(new SpinnerNumberModel(50000, 1000, 100000000, 5000));
            budgetSpin.setPreferredSize(new Dimension(130, 34));
            JSpinner.DefaultEditor ed = (JSpinner.DefaultEditor) budgetSpin.getEditor();
            ed.getTextField().setBackground(BG_INPUT);
            ed.getTextField().setForeground(TEXT);
            ed.getTextField().setCaretColor(TEXT);
            ed.getTextField().setFont(BODY);

            JButton find = btn("Find My Stocks", ACCENT);
            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            controls.setOpaque(false);
            controls.add(muted("My style:")); controls.add(profileBox);
            controls.add(muted("Budget (Rs.):")); controls.add(budgetSpin);
            controls.add(muted("Region:")); controls.add(regionBox);
            controls.add(find);

            profileDesc = new JLabel(" ");
            profileDesc.setFont(SMALL);
            profileDesc.setForeground(TEXT_MUTED);
            profileDesc.setBorder(new EmptyBorder(8, 4, 12, 0));

            finderM = new DefaultTableModel(
                    new Object[]{"#", "Symbol", "Company", "Sector", "1Y Return", "Risk", "Trend", "Match"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            finderT = darkTable(finderM);
            finderT.setRowHeight(32);
            finderT.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            finderT.getColumnModel().getColumn(4).setCellRenderer(colorR());
            finderT.getColumnModel().getColumn(5).setCellRenderer(new TagR(false));
            finderT.getColumnModel().getColumn(6).setCellRenderer(new TagR(false));
            finderT.getColumnModel().getColumn(7).setCellRenderer(new TagR(true));
            int[] widths = {32, 90, 150, 90, 80, 70, 85, 60};
            for (int i = 0; i < widths.length; i++) finderT.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            finderT.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                int r = finderT.getSelectedRow();
                if (r < 0) return;
                Company c = findCompany((String) finderM.getValueAt(r, 1));
                guideChart.setCompany(c);
                for (Pick p : picks) if (p.c == c) {
                    whyL.setText("<html><div style='width:1000px;color:#c8cee0;'><b><font color='#639bff'>" + c.symbol
                            + "</font></b>  ·  " + DESC.getOrDefault(c.symbol, c.name) + "<br>" + p.why + "</div></html>");
                    break;
                }
            });

            JPanel row = eastGap(scroll(finderT), guideChart);
            row.setPreferredSize(new Dimension(100, 470));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 470));
            guideChart.setPreferredSize(new Dimension(560, 470));

            whyL = new JLabel(" ");
            whyL.setFont(BODY);
            JPanel whyCard = infoCard("WHY THIS PICK", whyL, ACCENT);

            planL = new JLabel(" ");
            planL.setFont(BODY);
            JPanel planCard = infoCard("STARTER PORTFOLIO  ·  spread your money across different sectors", planL, SUCCESS);

            JButton addSel = btn("★  Add Selected to Watchlist", BG_INPUT);
            addSel.addActionListener(e -> {
                int r = finderT.getSelectedRow();
                if (r < 0) { setStatus("Select a company first.", true); return; }
                String sym = (String) finderM.getValueAt(r, 1);
                setStatus(addWatch(sym) ? sym + " added to watchlist." : sym + " is already in your watchlist.", false);
            });
            JButton openTrade = btn("Open Selected in Trade", ACCENT);
            openTrade.addActionListener(e -> {
                int r = finderT.getSelectedRow();
                if (r < 0) { setStatus("Select a company first.", true); return; }
                openInTrade((String) finderM.getValueAt(r, 1));
            });
            JButton addPlan = btn("Add Starter Portfolio to Watchlist", SUCCESS);
            addPlan.addActionListener(e -> {
                int added = 0;
                for (Pick p : planPicks) if (addWatch(p.c.symbol)) added++;
                setStatus(added + " stock(s) from the starter portfolio added to watchlist.", false);
            });
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            actions.setOpaque(false);
            actions.setBorder(new EmptyBorder(0, 0, 12, 0));
            actions.add(addSel); actions.add(openTrade); actions.add(addPlan);

            JLabel disc = muted("Educational tool using simulated data. Scores combine risk, 1-year return, drawdown and trend "
                    + "for your chosen style. Not financial advice.");
            disc.setBorder(new EmptyBorder(0, 4, 12, 0));

            find.addActionListener(e -> runFinder());
            profileBox.addActionListener(e -> runFinder());
            regionBox.addActionListener(e -> runFinder());
            budgetSpin.addChangeListener(e -> buildPlan());

            JPanel spacer1 = new JPanel(); spacer1.setOpaque(false); spacer1.setPreferredSize(new Dimension(1, 12));
            JPanel spacer2 = new JPanel(); spacer2.setOpaque(false); spacer2.setPreferredSize(new Dimension(1, 12));
            runFinder();
            return pageOf(controls, profileDesc, row, spacer1, whyCard, spacer2, actions, planCard, disc);
        }

        JPanel infoCard(String title, JLabel body, Color accent) {
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(BG_CARD);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(BORDER),
                            new EmptyBorder(12, 18, 12, 18))));
            JLabel t = muted(title);
            t.setFont(SMALL_B);
            t.setForeground(accent);
            t.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(t); card.add(Box.createVerticalStrut(6)); card.add(body);
            JPanel w = new JPanel(new BorderLayout());
            w.setOpaque(false);
            w.setBorder(new EmptyBorder(0, 0, 12, 0));
            w.add(card, BorderLayout.CENTER);
            return w;
        }

        void runFinder() {
            if (finderM == null) return;
            String prof = (String) profileBox.getSelectedItem();
            String region = (String) regionBox.getSelectedItem();
            switch (prof) {
                case "Conservative": profileDesc.setText("Conservative: you want steadier stocks with small price swings and shallow drops. Slower growth, better sleep."); break;
                case "Aggressive":   profileDesc.setText("Aggressive: you accept big swings for a chance at higher growth. Favours strong momentum. Expect bumpier rides."); break;
                default:             profileDesc.setText("Balanced: a mix of growth and stability. A sensible default if you are new.");
            }
            picks.clear();
            for (Company c : COMPANIES) {
                if (region == null || region.equals("All Countries") || countryFlag(c.country).contains(region))
                    picks.add(analyze(c, prof));
            }
            picks.sort((a, b) -> Double.compare(b.score, a.score));
            finderM.setRowCount(0);
            int rank = 1;
            for (Pick p : picks) {
                finderM.addRow(new Object[]{rank++, p.c.symbol, p.c.name, p.c.sector,
                        pct(p.ret1y), p.risk, p.trend, String.valueOf(Math.round(p.score))});
                if (rank > 10) break;
            }
            if (finderM.getRowCount() > 0) finderT.setRowSelectionInterval(0, 0);
            buildPlan();
        }

        void buildPlan() {
            if (planL == null) return;
            double budget = ((Number) budgetSpin.getValue()).doubleValue();
            List<Pick> plan = new ArrayList<>();
            Set<String> secs = new HashSet<>();
            for (Pick p : picks) { if (plan.size() >= 5) break; if (secs.add(p.c.sector)) plan.add(p); }
            for (Pick p : picks) { if (plan.size() >= Math.min(5, picks.size())) break; if (!plan.contains(p)) plan.add(p); }
            planPicks = plan;
            if (plan.isEmpty()) { planL.setText("No companies match this region."); return; }
            double invest = budget * 0.9, sumScore = 0, used = 0;
            for (Pick p : plan) sumScore += p.score;
            StringBuilder sb = new StringBuilder("<html><div style='width:1000px;color:#c8cee0;'>");
            sb.append("Suggested split of <b>").append(inr(budget)).append("</b>, keeping about 10% as cash:<br>");
            sb.append("<table cellpadding='4' cellspacing='0'>");
            for (Pick p : plan) {
                double w = p.score / sumScore;
                int sh = (int) Math.floor(invest * w / p.c.price);
                used += sh * p.c.price;
                sb.append("<tr><td width='110'><b><font color='#639bff'>").append(p.c.symbol).append("</font></b></td>")
                  .append("<td width='190'>").append(p.c.name.replace("&", "&amp;")).append("</td>")
                  .append("<td width='80'>").append(String.format("%.0f%%", w * 100)).append("</td><td>");
                if (sh > 0) sb.append(sh).append(" shares @ ").append(inr(p.c.price)).append("  =  ").append(inr(sh * p.c.price));
                else sb.append("<font color='#ffc35a'>One share costs ").append(inr(p.c.price)).append(" (more than its share of your budget)</font>");
                sb.append("</td></tr>");
            }
            sb.append("</table>Cash left over: <b>").append(inr(budget - used)).append("</b></div></html>");
            planL.setText(sb.toString());
        }

        boolean addWatch(String sym) {
            if (watchlist.add(sym)) { refreshWatch(); return true; }
            return false;
        }

        void openInTrade(String sym) {
            for (int i = 0; i < COMPANIES.size(); i++) {
                if (COMPANIES.get(i).symbol.equals(sym) && tradeCombo != null) {
                    tradeCombo.setSelectedIndex(i);
                    mainTabs.setSelectedIndex(2);
                    setStatus(sym + " loaded in the Trade tab.", false);
                    return;
                }
            }
        }

        // ---- Page 3: How to Choose ----
        JScrollPane choosePage() {
            return pageOf(
                guideCard("The 6-question checklist before you buy any stock",
                    "<b>1. Do I understand what this company does?</b> Read its one-line description on the <b>Companies</b> page. "
                    + "If you can't explain how it makes money, skip it for now.<br><br>"
                    + "<b>2. What is the trend?</b> Open the 1Y chart. Is the price above the yellow <b>MA</b> line and rising? "
                    + "Buying strength is usually safer than catching a falling stock.<br><br>"
                    + "<b>3. How bumpy is the ride?</b> Check <b>Risk</b> in the Stock Finder. High-risk stocks can swing 3-5% in a day. "
                    + "Only pick them if you will stay calm.<br><br>"
                    + "<b>4. Am I spreading my money?</b> Hold 4-6 stocks from <b>different sectors</b>. Keep any single stock under about 25% of your money.<br><br>"
                    + "<b>5. How much am I buying?</b> Start small. Keep at least 10-20% in cash so you can buy dips or recover from mistakes.<br><br>"
                    + "<b>6. When will I sell?</b> Decide before you buy: for example 'sell if it falls 10% below my price, or review at +20%'."),
                guideCard("Match your style",
                    table(new String[][]{
                        {"Conservative", "Large, steady companies: banks, FMCG, big IT services, utilities. Small swings, slower growth. Good for beginners who dislike big drops."},
                        {"Balanced", "A mix of steady names plus a couple of growth names (tech, autos). Aim for roughly 60% steady and 40% growth."},
                        {"Aggressive", "Growth and momentum: semiconductors, e-commerce, EVs. Can gain fast and also fall fast. Keep position sizes small."}}, 120)),
                guideCard("Sector cheat sheet: what moves each sector",
                    table(new String[][]{
                        {"Banking", "Interest rates and loan demand. Usually steady and a core holding. <font color='#4cd991'>Lower risk</font>."},
                        {"IT / Software", "Global client spending and the rupee/dollar rate. Steady earners. <font color='#4cd991'>Low-medium risk</font>."},
                        {"Technology / Semis", "New products and AI demand. High growth, big swings. <font color='#ff606e'>Higher risk</font>."},
                        {"Auto / Automotive", "Sales numbers, fuel prices, EV trends. <font color='#ffc35a'>Medium risk</font>."},
                        {"Energy", "Oil and gas prices. Cyclical. <font color='#ffc35a'>Medium risk</font>."},
                        {"FMCG / Pharma", "Everyday-use products and medicines. Demand holds in bad times. <font color='#4cd991'>Lower risk</font>."},
                        {"Telecom / Infra", "Subscriber growth, government projects. <font color='#4cd991'>Low-medium risk</font>."},
                        {"E-Commerce", "Consumer spending and competition. <font color='#ff606e'>Higher risk</font>."}}, 150)),
                guideCard("Worked example with Rs.1,00,000",
                    "A sensible first portfolio spreads money across sectors:<br>"
                    + "&bull; <b>Rs.20,000</b> a bank (e.g. HDFCBANK)<br>"
                    + "&bull; <b>Rs.20,000</b> an IT services company (e.g. TCS)<br>"
                    + "&bull; <b>Rs.20,000</b> an everyday-demand company (e.g. ASIANPAINT)<br>"
                    + "&bull; <b>Rs.15,000</b> a growth company (e.g. MSFT or NVDA)<br>"
                    + "&bull; <b>Rs.10,000</b> an auto or energy name<br>"
                    + "&bull; <b>Rs.15,000</b> kept as cash<br><br>"
                    + "The <b>Stock Finder</b> builds a split like this for you based on your style and budget."),
                guideCard("When to sell or review",
                    "&bull; The reason you bought has changed (the company's story got worse).<br>"
                    + "&bull; The price falls below your planned loss limit (for example 10% under your buy price).<br>"
                    + "&bull; A stock has grown to more than 25-30% of your portfolio. Trim it to rebalance.<br>"
                    + "&bull; You need the cash for something else.<br><br>"
                    + "<b>Don't</b> sell just because of one red day. Check the 3M and 1Y chart first."));
        }

        // ---- Page 4: Companies Explained ----
        JPanel companiesPage() {
            JPanel p = new JPanel(new BorderLayout(0, 10));
            p.setOpaque(false);

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(muted("Every company in plain English, with a risk level based on how much its price swings."), BorderLayout.WEST);
            JPanel acts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            acts.setOpaque(false);
            JButton w = btn("★  Add to Watchlist", BG_INPUT);
            JButton t = btn("Open in Trade", ACCENT);
            acts.add(w); acts.add(t);
            top.add(acts, BorderLayout.EAST);

            DefaultTableModel m = new DefaultTableModel(
                    new Object[]{"Symbol", "Company", "What it does", "Sector", "Country", "Risk"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            for (Company c : COMPANIES)
                m.addRow(new Object[]{c.symbol, c.name, DESC.getOrDefault(c.symbol, ""), c.sector,
                        countryFlag(c.country), analyze(c, "Balanced").risk});
            compT = darkTable(m);
            compT.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            compT.getColumnModel().getColumn(5).setCellRenderer(new TagR(false));
            int[] cw = {90, 150, 520, 100, 100, 70};
            for (int i = 0; i < cw.length; i++) compT.getColumnModel().getColumn(i).setPreferredWidth(cw[i]);
            w.addActionListener(e -> {
                int r = compT.getSelectedRow();
                if (r < 0) { setStatus("Select a company first.", true); return; }
                String sym = (String) m.getValueAt(r, 0);
                setStatus(addWatch(sym) ? sym + " added to watchlist." : sym + " is already in your watchlist.", false);
            });
            t.addActionListener(e -> {
                int r = compT.getSelectedRow();
                if (r < 0) { setStatus("Select a company first.", true); return; }
                openInTrade((String) m.getValueAt(r, 0));
            });
            p.add(top, BorderLayout.NORTH);
            p.add(scroll(compT), BorderLayout.CENTER);
            return p;
        }

        // ---- Page 5: Read Charts ----
        JScrollPane chartsPage() {
            return pageOf(
                guideCard("The chart controls",
                    table(new String[][]{
                        {"1W / 1M / 3M / 6M / 1Y", "How far back to look. Start with <b>1Y</b> to see the big picture, then zoom in."},
                        {"Line", "Connects each day's closing price. Green means the stock is higher than at the start of the range; red means lower."},
                        {"Candles", "Shows more detail for each day (see below). Preferred by active traders."},
                        {"MA", "The yellow <b>moving average</b>: the average closing price of recent days. It smooths out noise."},
                        {"Hover", "Move your mouse over the chart to see the open, high, low, close and volume for that day."}}, 170)),
                guideCard("How to read a candle",
                    "Each candle is one trading day.<br>"
                    + "&bull; <b><font color='#4cd991'>Green candle</font></b>: the price closed <b>higher</b> than it opened.<br>"
                    + "&bull; <b><font color='#ff606e'>Red candle</font></b>: the price closed <b>lower</b> than it opened.<br>"
                    + "&bull; The thick <b>body</b> spans open to close. The thin lines (<b>wicks</b>) reach the day's high and low.<br>"
                    + "&bull; Long wicks mean the price moved a lot during the day but was pushed back."),
                guideCard("Three simple patterns",
                    table(new String[][]{
                        {"<font color='#4cd991'>Uptrend</font>", "Price is above the yellow MA line, and highs and lows keep getting higher. Usually a healthy sign."},
                        {"<font color='#ffc35a'>Sideways</font>", "Price wanders around the MA line without direction. Wait for a clear move."},
                        {"<font color='#ff606e'>Downtrend</font>", "Price is below the MA line and keeps making lower lows. Be careful about buying."}}, 130)),
                guideCard("Volume bars",
                    "The small bars at the bottom show how many shares traded each day. A price move on <b>high volume</b> is "
                    + "more meaningful than the same move on low volume. Rising price with rising volume is a strong sign."),
                guideCard("The stats under the chart",
                    table(new String[][]{
                        {"Day High / Low", "The highest and lowest prices on the latest day."},
                        {"Volume", "Shares traded on the latest day (K = thousand, M = million)."},
                        {"Range High / Low", "The highest and lowest prices within the selected range."},
                        {"Range Change", "How much the price changed from the start to the end of the selected range."}}, 150)));
        }

        // ---- Page 6: Glossary ----
        JScrollPane glossaryPage() {
            return pageOf(
                guideCard("Glossary: plain-English meanings",
                    table(new String[][]{
                        {"Share / Stock", "A small piece of ownership in a company."},
                        {"Symbol (Ticker)", "A short code for a company, like TCS or AAPL."},
                        {"Market order", "Buy or sell right now at the current price. This is what the Trade tab uses."},
                        {"Portfolio", "All the stocks you own."},
                        {"Average price", "What you paid per share on average across all your buys."},
                        {"P&amp;L", "Profit and Loss: current value minus what you invested."},
                        {"Return %", "Profit or loss as a percentage of what you invested."},
                        {"Volatility", "How much a price jumps around. Higher volatility means a bumpier ride and more risk."},
                        {"Drawdown", "The fall from a peak to a later low. 'Worst drop' in the finder."},
                        {"Diversification", "Spreading money over different stocks and sectors so one bad pick can't sink you."},
                        {"Sector", "A group of similar businesses, like Banking or IT."},
                        {"Bull / Bear market", "Bull: prices broadly rising. Bear: prices broadly falling."},
                        {"Moving average (MA)", "The average of recent closing prices. Shows the trend direction."},
                        {"Volume", "Number of shares traded in a period."},
                        {"Watchlist", "A list of stocks you follow without owning them."},
                        {"Dividend", "A share of company profit paid to shareholders (not simulated in this app)."}}, 170)),
                guideCard("Common beginner mistakes",
                    "&bull; <b>Putting everything in one stock.</b> Spread it out.<br>"
                    + "&bull; <b>Chasing a stock after a huge jump.</b> Check the trend and don't rush.<br>"
                    + "&bull; <b>Panic selling</b> after one bad day. Zoom out to 3M or 1Y first.<br>"
                    + "&bull; <b>Buying what you don't understand.</b> Read the company description.<br>"
                    + "&bull; <b>Using all your cash.</b> Always keep some in reserve.<br>"
                    + "&bull; <b>Trading too often.</b> More trades does not mean better results.", DANGER),
                guideCard("Admin features",
                    "If you log in with an administrator account (for the seed database: <b>investor01@example.test</b> / <b>Demo@123</b>), you get an extra "
                    + "<b>Admin</b> tab where you can add or remove users. This is for demo/administrative purposes only.", TEXT_MUTED));
        }

        // -------- ADMIN --------
        void refreshAdminUsers() {
            if (userM == null) return;
            try {
                Map<String, Object> response = API.get("/api/users");
                userM.setRowCount(0);
                for (Object item : asList(response.get("data"))) {
                    Map<String, Object> u = asMap(item);
                    userM.addRow(new Object[]{stringValue(u.get("email")), stringValue(u.get("name")),
                            stringValue(u.get("role")), inr(doubleValue(u.get("cash")))});
                }
            } catch (Exception ex) { setStatus("Could not load admin users: " + ex.getMessage(), true); }
        }

        JPanel adminTab() {
            JPanel p = new JPanel(new BorderLayout(10, 12));
            p.setBackground(BG_DEEP);
            p.setBorder(new EmptyBorder(14, 6, 6, 6));
            p.add(h1("Admin Panel"), BorderLayout.NORTH);

            userM = new DefaultTableModel(
                    new Object[]{"Email", "Display Name", "Role", "Cash"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            userT = darkTable(userM);
            userT.getColumnModel().getColumn(3).setCellRenderer(rightR());
            refreshAdminUsers();
            p.add(scroll(userT), BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            bottom.setOpaque(false);

            JButton add = btn("Add User", SUCCESS);
            add.addActionListener(e -> {
                JTextField emailField = input(); JPasswordField pw = passInput(); JTextField nameField = input();
                JPanel f = new JPanel(new GridLayout(0, 1, 6, 6)); f.setBackground(BG_CARD);
                f.add(muted("Email Address")); f.add(emailField);
                f.add(muted("Password (minimum 6 characters)")); f.add(pw);
                f.add(muted("Display Name")); f.add(nameField);
                int res = JOptionPane.showConfirmDialog(this, f, "Add User", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (res != JOptionPane.OK_OPTION) return;
                String email = emailField.getText().trim(), password = new String(pw.getPassword()), name = nameField.getText().trim();
                if (email.isEmpty() || password.length() < 6 || name.isEmpty()) { setStatus("Enter name, email and a password of at least 6 characters.", true); return; }
                try { API.post("/api/users", mapOf("email", email, "password", password, "name", name)); refreshAdminUsers(); setStatus("User added: " + email, false); }
                catch (Exception ex) { setStatus(ex.getMessage(), true); }
            });

            JButton del = btn("Delete Selected", DANGER);
            del.addActionListener(e -> {
                int r = userT.getSelectedRow();
                if (r == -1) { setStatus("Select a user first.", true); return; }
                String email = String.valueOf(userM.getValueAt(r, 0));
                if (email.equalsIgnoreCase(me.username)) { setStatus("You cannot delete your own account.", true); return; }
                try {
                    Map<String, Object> response = API.get("/api/users");
                    long id = -1;
                    for (Object item : asList(response.get("data"))) { Map<String,Object> u = asMap(item); if (email.equalsIgnoreCase(stringValue(u.get("email")))) id = longValue(u.get("user_id")); }
                    if (id < 0) { setStatus("User not found.", true); return; }
                    API.delete("/api/users/" + id); refreshAdminUsers(); setStatus("User deleted: " + email, false);
                } catch (Exception ex) { setStatus(ex.getMessage(), true); }
            });

            JButton diagnostics = btn("Backend OS / DBMS Status", ACCENT);
            diagnostics.addActionListener(e -> {
                try {
                    Map<String, Object> os = API.get("/api/os-status");
                    Map<String, Object> db = API.get("/api/dbms-status");
                    Map<String, Object> risk = API.get("/api/risk?iterations=5000");
                    String message = "OS STATUS\n" + os + "\n\nDBMS STATUS\n" + db
                            + "\n\nPARALLEL RISK ANALYSIS\n" + risk;
                    JTextArea area = new JTextArea(message, 22, 72);
                    area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true);
                    area.setBackground(BG_CARD); area.setForeground(TEXT); area.setCaretColor(TEXT);
                    JOptionPane.showMessageDialog(this, new JScrollPane(area), "Backend Diagnostics", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) { setStatus("Could not retrieve backend diagnostics: " + ex.getMessage(), true); }
            });
            bottom.add(diagnostics); bottom.add(add); bottom.add(del);
            p.add(bottom, BorderLayout.SOUTH);
            return p;
        }

        // -------- STATUS --------
        JPanel statusBar() {
            JPanel bar = new JPanel(new BorderLayout());
            bar.setBackground(BG_PANEL);
            bar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                    new EmptyBorder(8, 20, 8, 20)));
            status = new JLabel("Ready");
            status.setFont(SMALL);
            status.setForeground(TEXT_MUTED);
            bar.add(status, BorderLayout.WEST);
            JLabel v = new JLabel("SSA Pro v5.2  ·  Chart data is simulated");
            v.setFont(SMALL);
            v.setForeground(TEXT_MUTED);
            bar.add(v, BorderLayout.EAST);
            return bar;
        }

        void setStatus(String msg, boolean error) {
            if (status == null) return;
            status.setText(msg);
            status.setForeground(error ? DANGER : SUCCESS);
        }
    }
}
