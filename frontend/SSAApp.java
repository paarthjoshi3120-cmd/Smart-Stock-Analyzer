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
