package com.nexusclient.launcher.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * MicrosoftAuth – signs a player in to Minecraft with their Microsoft account.
 *
 * Minecraft login is a chain of four "swaps", each giving a pass for the next:
 *
 *   1. Microsoft  – the player logs in on Microsoft's own web page, in their browser.
 *                   Nexus never sees the password; Microsoft gives us a pass ("token") instead.
 *   2. Xbox Live  – swap the Microsoft pass for an Xbox Live pass.
 *   3. XSTS       – swap that for a pass that works with Minecraft's servers.
 *   4. Minecraft  – swap that for the Minecraft pass the game uses, then fetch the player's
 *                   name, unique ID and skin.
 *
 * Microsoft also gives a "refresh token": a long-lasting pass that lets us repeat the chain later
 * WITHOUT the player logging in again. That's the one secret we save (in SecretStore).
 */
public final class MicrosoftAuth {

    /**
     * Nexus's app ID from the Azure app registration. It is NOT a secret – every desktop app's ID
     * is public. It only works for Minecraft after Mojang approves it (the app ID review).
     */
    public static final String CLIENT_ID = "b4c7e154-4e31-4ea5-8d5c-2f87357081b5";

    // "consumers" = personal Microsoft accounts (the kind Minecraft players use).
    private static final String AUTHORIZE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String SCOPE = "XboxLive.signin offline_access"; // offline_access = give us a refresh token
    private static final String XBOX_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MINECRAFT_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MINECRAFT_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    /** How long to wait for the player to finish logging in in their browser. */
    private static final Duration BROWSER_TIMEOUT = Duration.ofMinutes(5);

    /**
     * The result of a successful sign-in.
     *
     * @param account      the player, with a fresh Minecraft pass – ready to start the game
     * @param refreshToken the new long-lasting pass to save for next time
     * @param skinUrl      link to the player's skin picture (null if they have none)
     */
    public record LoginResult(Account account, String refreshToken, String skinUrl) {}

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // ================================================================== step 1: Microsoft

    /**
     * Signs a player in by opening Microsoft's login page in their browser.
     * This waits (up to 5 minutes) until they finish, so run it on a background thread.
     *
     * How the browser hands the result back to us: Nexus briefly runs a tiny web server on this
     * computer ("localhost"). After login, Microsoft sends the browser to that server's address
     * with a one-time code in the link, and the server passes the code to us.
     *
     * @param openBrowser opens a web address in the player's browser
     */
    public LoginResult signIn(Consumer<String> openBrowser) throws LoginException, InterruptedException {
        // PKCE: a random secret ("verifier") that only we know, and its fingerprint ("challenge").
        // Microsoft only hands out tokens to whoever can show the original verifier, so a stolen
        // one-time code is useless to anyone else.
        String verifier = randomText(64);
        String challenge = base64Url(sha256(verifier));
        String state = randomText(16); // checked when the browser comes back, to block fake requests

        CompletableFuture<Map<String, String>> browserResult = new CompletableFuture<>();
        HttpServer server;
        try {
            // Port 0 = "pick any free port". Only reachable from this computer (loopback address).
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new LoginException("Nexus couldn't start its sign-in helper. Please try again.", e);
        }
        server.createContext("/", exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            boolean finished = params.containsKey("code") || params.containsKey("error");
            byte[] page = browserPage(params.containsKey("code")).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, page.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(page);
            }
            if (finished) {
                browserResult.complete(params);
            }
        });
        server.start();

        try {
            String redirectUri = "http://localhost:" + server.getAddress().getPort();
            Map<String, String> query = new LinkedHashMap<>();
            query.put("client_id", CLIENT_ID);
            query.put("response_type", "code");
            query.put("redirect_uri", redirectUri);
            query.put("scope", SCOPE);
            query.put("code_challenge", challenge);
            query.put("code_challenge_method", "S256");
            query.put("state", state);
            query.put("prompt", "select_account"); // always let them choose which account
            openBrowser.accept(AUTHORIZE_URL + "?" + formEncode(query));

            Map<String, String> result;
            try {
                result = browserResult.get(BROWSER_TIMEOUT.toMinutes(), TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                throw new LoginException("Sign-in timed out. Please try again.");
            } catch (ExecutionException e) {
                throw new LoginException("Sign-in failed. Please try again.", e);
            }

            if (result.containsKey("error")) {
                if ("access_denied".equals(result.get("error"))) {
                    throw new LoginException("Sign-in was cancelled.");
                }
                throw new LoginException("Microsoft sign-in failed: "
                        + result.getOrDefault("error_description", result.get("error")));
            }
            if (!state.equals(result.get("state"))) {
                throw new LoginException("Sign-in failed a safety check. Please try again.");
            }

            // Swap the one-time code for Microsoft tokens.
            Map<String, String> form = new LinkedHashMap<>();
            form.put("client_id", CLIENT_ID);
            form.put("grant_type", "authorization_code");
            form.put("code", result.get("code"));
            form.put("redirect_uri", redirectUri);
            form.put("code_verifier", verifier);
            form.put("scope", SCOPE);
            return continueWithXbox(postForm(TOKEN_URL, form));
        } finally {
            server.stop(0); // shut the helper server down straight away
        }
    }

    /**
     * Signs in again using a saved refresh token – no browser, no password. Used before each launch
     * so the Minecraft pass is always fresh. Run it on a background thread.
     */
    public LoginResult refresh(String refreshToken) throws LoginException, InterruptedException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", CLIENT_ID);
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("scope", SCOPE);
        return continueWithXbox(postForm(TOKEN_URL, form));
    }

    // ================================================================== steps 2–4

    /** Steps 2, 3 and 4: Xbox Live, XSTS, Minecraft. */
    private LoginResult continueWithXbox(JsonObject microsoftTokens) throws LoginException, InterruptedException {
        String microsoftToken = microsoftTokens.get("access_token").getAsString();
        String refreshToken = microsoftTokens.get("refresh_token").getAsString();

        // --- 2. Xbox Live ---
        JsonObject xboxRequest = new JsonObject();
        JsonObject xboxProps = new JsonObject();
        xboxProps.addProperty("AuthMethod", "RPS");
        xboxProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xboxProps.addProperty("RpsTicket", "d=" + microsoftToken);
        xboxRequest.add("Properties", xboxProps);
        xboxRequest.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xboxRequest.addProperty("TokenType", "JWT");
        HttpResponse<String> xbox = postJson(XBOX_URL, xboxRequest, null);
        if (xbox.statusCode() != 200) {
            throw new LoginException("Xbox Live sign-in failed (error " + xbox.statusCode() + "). Please try again.");
        }
        JsonObject xboxJson = JsonParser.parseString(xbox.body()).getAsJsonObject();
        String xboxToken = xboxJson.get("Token").getAsString();
        String userHash = xboxJson.getAsJsonObject("DisplayClaims").getAsJsonArray("xui")
                .get(0).getAsJsonObject().get("uhs").getAsString();

        // --- 3. XSTS (a pass for Minecraft's servers) ---
        JsonObject xstsRequest = new JsonObject();
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(xboxToken);
        xstsProps.add("UserTokens", userTokens);
        xstsRequest.add("Properties", xstsProps);
        xstsRequest.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsRequest.addProperty("TokenType", "JWT");
        HttpResponse<String> xsts = postJson(XSTS_URL, xstsRequest, null);
        if (xsts.statusCode() == 401) {
            throw new LoginException(explainXboxError(xsts.body()));
        }
        if (xsts.statusCode() != 200) {
            throw new LoginException("Xbox sign-in failed (error " + xsts.statusCode() + "). Please try again.");
        }
        String xstsToken = JsonParser.parseString(xsts.body()).getAsJsonObject().get("Token").getAsString();

        // --- 4. Minecraft ---
        JsonObject minecraftRequest = new JsonObject();
        minecraftRequest.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        HttpResponse<String> minecraft = postJson(MINECRAFT_LOGIN_URL, minecraftRequest, null);
        if (minecraft.statusCode() == 403) {
            throw new LoginException("Your Microsoft sign-in worked, but Mojang hasn't approved Nexus to log "
                    + "players into Minecraft yet. This starts working as soon as the app ID review is approved.");
        }
        if (minecraft.statusCode() == 429) {
            throw new LoginException("Minecraft's login servers are busy. Please wait a minute and try again.");
        }
        if (minecraft.statusCode() != 200) {
            throw new LoginException("Minecraft sign-in failed (error " + minecraft.statusCode() + "). Please try again.");
        }
        String minecraftToken = JsonParser.parseString(minecraft.body()).getAsJsonObject()
                .get("access_token").getAsString();

        // --- The player's Minecraft profile: name, unique ID, skin ---
        HttpResponse<String> profile = send(HttpRequest.newBuilder(URI.create(MINECRAFT_PROFILE_URL))
                .header("Authorization", "Bearer " + minecraftToken)
                .GET().build());
        if (profile.statusCode() == 404) {
            throw new LoginException("This Microsoft account doesn't own Minecraft: Java Edition "
                    + "(or hasn't picked a Minecraft name yet at minecraft.net).");
        }
        if (profile.statusCode() != 200) {
            throw new LoginException("Couldn't load your Minecraft profile (error " + profile.statusCode() + ").");
        }
        JsonObject profileJson = JsonParser.parseString(profile.body()).getAsJsonObject();
        String uuid = profileJson.get("id").getAsString();
        String name = profileJson.get("name").getAsString();
        String skinUrl = null;
        if (profileJson.has("skins")) {
            for (JsonElement skin : profileJson.getAsJsonArray("skins")) {
                JsonObject skinJson = skin.getAsJsonObject();
                if ("ACTIVE".equals(skinJson.get("state").getAsString())) {
                    skinUrl = skinJson.get("url").getAsString();
                }
            }
        }
        return new LoginResult(new Account(name, uuid, minecraftToken, "msa"), refreshToken, skinUrl);
    }

    /** Turns Xbox's error numbers into plain words. */
    private static String explainXboxError(String body) {
        long code = 0;
        try {
            code = JsonParser.parseString(body).getAsJsonObject().get("XErr").getAsLong();
        } catch (RuntimeException ignored) {
            // no error number – use the general message below
        }
        if (code == 2148916233L) {
            return "This Microsoft account doesn't have an Xbox profile yet. Sign in once at xbox.com "
                    + "to create one, then try again.";
        }
        if (code == 2148916235L) {
            return "Xbox Live isn't available in your country, so Minecraft sign-in isn't possible.";
        }
        if (code == 2148916236L || code == 2148916237L) {
            return "This account needs adult verification on xbox.com before it can sign in.";
        }
        if (code == 2148916238L) {
            return "This is a child account. An adult needs to add it to a Microsoft Family group "
                    + "(at account.microsoft.com/family) before it can play.";
        }
        return "Xbox sign-in was refused (error " + code + ").";
    }

    // ================================================================== web helpers

    /** Sends form data (like a web page form) and returns the JSON reply. Used for Microsoft tokens. */
    private JsonObject postForm(String url, Map<String, String> form) throws LoginException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(form)))
                .build());
        JsonObject json;
        try {
            json = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (RuntimeException e) { // the reply wasn't JSON (e.g. a server error page)
            throw new LoginException("Microsoft sign-in failed (error " + response.statusCode() + "). Please try again.", e);
        }
        if (response.statusCode() != 200) {
            String error = json.has("error") ? json.get("error").getAsString() : "";
            if ("invalid_grant".equals(error)) {
                throw new LoginException("Your saved sign-in has expired. Please sign in again.");
            }
            String detail = json.has("error_description") ? json.get("error_description").getAsString() : error;
            throw new LoginException("Microsoft sign-in failed: " + detail);
        }
        return json;
    }

    /** Sends JSON and returns the reply. Used for Xbox and Minecraft. */
    private HttpResponse<String> postJson(String url, JsonObject body, String bearerToken)
            throws LoginException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        return send(request.build());
    }

    private HttpResponse<String> send(HttpRequest request) throws LoginException, InterruptedException {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new LoginException("Couldn't reach the sign-in servers. Check your internet connection.", e);
        }
    }

    /** The page the browser shows after login. */
    private static String browserPage(boolean success) {
        String message = success
                ? "You're signed in! You can close this tab and go back to Nexus."
                : "Sign-in didn't finish. You can close this tab and try again in Nexus.";
        return "<!doctype html><html><head><meta charset='utf-8'><title>Nexus Client</title></head>"
                + "<body style='margin:0;height:100vh;display:flex;align-items:center;justify-content:center;"
                + "background:#08080D;color:#E4E4EE;font-family:Segoe UI,Arial,sans-serif'>"
                + "<div style='text-align:center'><div style='font-size:28px;font-weight:bold;color:#00E5FF'>NEXUS</div>"
                + "<p style='font-size:16px'>" + message + "</p></div></body></html>";
    }

    /** Reads "a=1&b=2" into a map. */
    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                params.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    /** Writes a map as "a=1&b=2", safely escaping special characters. */
    private static String formEncode(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    /** Random text that's safe to put in a web address. */
    private static String randomText(int bytes) {
        byte[] random = new byte[bytes];
        new SecureRandom().nextBytes(random);
        return base64Url(random);
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in Java", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
