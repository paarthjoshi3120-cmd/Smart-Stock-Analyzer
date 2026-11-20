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
