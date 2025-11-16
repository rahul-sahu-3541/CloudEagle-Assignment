package org.example.cloudeagle;

import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class DropboxOAuth {
    private static final String CLIENT_ID = "oxir35dszaxq41w";
    private static final String CLIENT_SECRET = "bw8tbpq7u2bcc1u";
    private static final String REDIRECT_URI = "https://oauth.pstmn.io/v1/callback";

    private static final String AUTH_URL = "https://www.dropbox.com/oauth2/authorize";
    private static final String TOKEN_URL = "https://api.dropboxapi.com/oauth2/token";
    private static final String TEAM_INFO_URL = "https://api.dropboxapi.com/2/team/get_info";

    private static HttpClient http = HttpClient.newHttpClient();
    private static ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        String scope = "team_info.read members.read events.read";

        String authUrl = String.format("%s?client_id=%s&response_type=code&redirect_uri=%s&token_access_type=offline&scope=%s",
                AUTH_URL, URIEncoder.encode(CLIENT_ID), URIEncoder.encode(REDIRECT_URI), URIEncoder.encode(scope));
        System.out.println("Open the following URL in your browser and authorize using team admin account:");
        System.out.println(authUrl);
        System.out.println("\nAfter authorizing, paste the 'code' parameter from the redirect here:");

        // Read auth code from stdin
        Scanner scanner = new Scanner(System.in);
        String code = scanner.nextLine().trim();

        // Exchange authorization code for access token
        String token = exchangeCodeForToken(code);
        if (token == null) {
            System.err.println("Failed to obtain access token.");
            return;
        }

        System.out.println("Access token obtained. Calling team/get_info ...");
        fetchTeamInfo(token);
    }

    private static String exchangeCodeForToken(String code) throws IOException, InterruptedException {
        String body = "code=" + URIEncoder.encode(code)
                + "&grant_type=authorization_code"
                + "&client_id=" + URIEncoder.encode(CLIENT_ID)
                + "&client_secret=" + URIEncoder.encode(CLIENT_SECRET)
                + "&redirect_uri=" + URIEncoder.encode(REDIRECT_URI);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            System.err.println("Token exchange failed: " + resp.statusCode() + " / " + resp.body());
            return null;
        }
        JsonNode json = mapper.readTree(resp.body());
        String accessToken = json.path("access_token").asText(null);

        return accessToken;
    }

    private static void fetchTeamInfo(String accessToken) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(TEAM_INFO_URL))
                .header("Authorization", "Bearer " + accessToken)
                .POST(BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            System.out.println("Team info response:");
            Object json = mapper.readValue(resp.body(), Object.class);
            String pretty = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            System.out.println(pretty);
        } else {
            System.err.println("Failed to call team/get_info. Status: " + resp.statusCode());
            System.err.println(resp.body());
        }
    }

    static class URIEncoder {
        static String encode(String s) {
            try {
                return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8.toString());
            } catch (Exception e) {
                return s;
            }
        }
    }
}
