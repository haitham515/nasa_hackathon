package space.astra.ai;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** HTTP client for the Python AI service (scoring + replay engine). */
@Component
public class AiClient {

    private final RestClient http;
    private final tools.jackson.databind.json.JsonMapper mapper;

    public AiClient(@Value("${astra.ai-url}") String baseUrl, tools.jackson.databind.json.JsonMapper mapper) {
        // Force HTTP/1.1: the JDK client otherwise sends "Upgrade: h2c", and uvicorn
        // drops the request body of upgrade requests (FastAPI then reports "body missing").
        java.net.http.HttpClient jdk = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .connectTimeout(java.time.Duration.ofSeconds(3))
                .build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.mapper = mapper;
    }

    public Map<String, Object> scoreBiomarkers(String participantId, String timepoint, String sampleName,
                                               Map<String, Double> percents) {
        return post("/score/biomarkers", Map.of(
                "participant_id", participantId,
                "timepoint", timepoint,
                "sample_name", sampleName == null ? participantId + "_serum_" + timepoint : sampleName,
                "percents", percents));
    }

    public Map<String, Object> scoreVitals(String participantId, double hrBpm, double hrvRmssdMs) {
        return post("/score/vitals", Map.of(
                "participant_id", participantId, "hr_bpm", hrBpm, "hrv_rmssd_ms", hrvRmssdMs));
    }

    public Map<String, Object> startReplay(long runId, double speed, boolean simulate) {
        return post("/replay/start", Map.of("run_id", runId, "speed", speed, "simulate", simulate));
    }

    public Map<String, Object> stopReplay() {
        return post("/replay/stop", Map.of());
    }

    public Map<String, Object> replayStatus() {
        return get("/replay/status");
    }

    public Map<String, Object> modelCard() {
        return get("/model/card");
    }

    public boolean isUp() {
        try {
            return "UP".equals(get("/health").get("status"));
        } catch (RestClientException e) {
            return false;
        }
    }

    private Map<String, Object> post(String path, Object body) {
        String json = mapper.writeValueAsString(body);
        String response = http.post().uri(path).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(json).retrieve().body(String.class);
        return parse(response);
    }

    private Map<String, Object> get(String path) {
        return parse(http.get().uri(path).retrieve().body(String.class));
    }

    private Map<String, Object> parse(String response) {
        if (response == null || response.isBlank()) return Map.of();
        return mapper.readValue(response, new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    /** Helpers to read the untyped JSON maps returned by the AI service. */
    public static double num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    public static boolean bool(Map<String, Object> m, String key) {
        return Boolean.TRUE.equals(m.get(key));
    }

    @SuppressWarnings("unchecked")
    public static List<String> strings(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof List<?> l ? (List<String>) l : List.of();
    }
}
