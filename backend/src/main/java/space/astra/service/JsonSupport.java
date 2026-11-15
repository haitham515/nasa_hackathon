package space.astra.service;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Serialises the AI service's free-form result maps to/from the details_json column. */
@Component
public class JsonSupport {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final JsonMapper mapper;

    public JsonSupport(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    public Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        return mapper.readValue(json, MAP);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> listOfMaps(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }
}
