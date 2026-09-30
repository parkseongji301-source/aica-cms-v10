package egovframework.backoffice.integration;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP + cookie sessions + CSRF tokens from rendered forms; no mocked authentication. */
final class HttpBrowser {
    private final String base;
    private final HttpClient client;
    HttpBrowser(int port) {
        base = "http://127.0.0.1:" + port;
        client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();
    }
    HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    HttpResponse<String> post(String path, Map<String, String> fields) throws Exception {
        var login = get("/login");
        assertThat(login.statusCode()).isEqualTo(200);
        var values = new LinkedHashMap<>(fields);
        values.put("_csrf", extract(login.body(), "name=\"_csrf\"[^>]*value=\"([^\"]+)\""));
        return rawPost(path, values);
    }
    HttpResponse<String> rawPost(String path, Map<String, String> fields) throws Exception {
        String form = fields.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    HttpResponse<String> upload(String path, byte[] image) throws Exception {
        String csrf=extract(get("/login").body(), "name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
        String boundary="AicaTestBoundary123";
        String header="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"upload.png\"\r\nContent-Type: image/png\r\n\r\n";
        var body=HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofString(header),HttpRequest.BodyPublishers.ofByteArray(image),HttpRequest.BodyPublishers.ofString("\r\n--"+boundary+"--\r\n"));
        return client.send(HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","multipart/form-data; boundary="+boundary).header("X-CSRF-TOKEN",csrf).POST(body).build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    HttpResponse<String> json(String method, String path, String body, String csrf) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(20))
                .header("Content-Type","application/json");
        if(csrf!=null) request.header("X-CSRF-TOKEN",csrf);
        return client.send(request.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    void login(String email, String password, String destination) throws Exception {
        redirect(post("/login", Map.of("username", email, "password", password)), destination);
    }
    void password(String current, String next) throws Exception {
        redirect(post("/account/password", Map.of("currentPassword", current, "newPassword", next,
                "confirmPassword", next)), "/login?changed");
    }
    String issued() throws Exception {
        var response = get("/admin/legacy/accounts/issued");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("cache-control").orElse("")).contains("no-store");
        return extract(response.body(), "id=\"temporary-password\"[^>]*>([^<]+)</code>");
    }
    static String location(HttpResponse<String> response) {
        return response.headers().firstValue("location").orElse("");
    }
    static void redirect(HttpResponse<String> response, String target) {
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(location(response)).endsWith(target);
    }
    static String extract(String text, String regex) {
        var match = Pattern.compile(regex).matcher(text);
        assertThat(match.find()).as("Expected field in rendered HTML: " + regex).isTrue();
        return match.group(1);
    }
    private static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
}
