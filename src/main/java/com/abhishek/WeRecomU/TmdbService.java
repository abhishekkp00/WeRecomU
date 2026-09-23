package com.abhishek.WeRecomU;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TmdbService {

    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final String accessToken;
    private final Map<String, String> posterCache = new ConcurrentHashMap<>();

    public TmdbService(
            JsonMapper jsonMapper,
            @Value("${tmdb.api.read-access-token:}") String accessToken) {
        this.jsonMapper = jsonMapper;
        this.accessToken = accessToken;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.themoviedb.org/3")
                .build();
    }

    public String getPosterUrl(String title) {
        if (title == null || title.isBlank() || accessToken.isBlank()) {
            return null;
        }

        String cacheKey = title.trim().toLowerCase();
        if (posterCache.containsKey(cacheKey)) {
            String cached = posterCache.get(cacheKey);
            return cached.isBlank() ? null : cached;
        }

        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/movie")
                            .queryParam("query", title)
                            .queryParam("include_adult", false)
                            .queryParam("language", "en-US")
                            .queryParam("page", 1)
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(String.class);

            Map<String, Object> payload = jsonMapper.readValue(
                    response,
                    new TypeReference<>() {}
            );

            Object rawResults = payload.get("results");
            if (!(rawResults instanceof List<?> results) || results.isEmpty()) {
                posterCache.put(cacheKey, "");
                return null;
            }

            Map<String, Object> bestMatch = findBestMatch(title, results);
            Object posterPath = bestMatch.get("poster_path");

            if (!(posterPath instanceof String path) || path.isBlank()) {
                posterCache.put(cacheKey, "");
                return null;
            }

            String posterUrl = IMAGE_BASE_URL + path;
            posterCache.put(cacheKey, posterUrl);
            return posterUrl;

        } catch (RestClientException | java.io.IOException e) {
            System.err.println("TMDB poster lookup failed for '" + title + "': " + e.getMessage());
            posterCache.put(cacheKey, "");
            return null;
        }
    }

    private Map<String, Object> findBestMatch(String title, List<?> results) {
        String normalizedTitle = title.trim().toLowerCase();

        for (Object result : results) {
            if (result instanceof Map<?, ?> rawMovie) {
                Object tmdbTitle = rawMovie.get("title");
                if (tmdbTitle instanceof String value
                        && value.trim().toLowerCase().equals(normalizedTitle)) {
                    return castMap(rawMovie);
                }
            }
        }

        return castMap((Map<?, ?>) results.getFirst());
    }

    private Map<String, Object> castMap(Map<?, ?> source) {
        return source.entrySet().stream().collect(
                java.util.stream.Collectors.toMap(
                        entry -> String.valueOf(entry.getKey()),
                        Map.Entry::getValue
                )
        );
    }
}
