package com.harmless004.aicopilot.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Service responsible for communicating with AI APIs (OpenAI, Claude, etc.)
 * to generate code completions.
 */
@Service
public final class AIService {

    private static final Logger LOG = Logger.getInstance(AIService.class);

    // API Configuration
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    // Threading and caching
    private final Executor aiExecutor = AppExecutorUtil.getAppExecutorService();
    private final ConcurrentHashMap<String, String> responseCache = new ConcurrentHashMap<>();
    private final HttpClient httpClient;

    public AIService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Main method to get AI completion for given code context
     */
    public CompletableFuture<String> getCompletion(@NotNull String codeContext, @NotNull String currentLine) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check cache first
                String cacheKey = generateCacheKey(codeContext, currentLine);
                String cachedResponse = responseCache.get(cacheKey);
                if (cachedResponse != null) {
                    LOG.info("Using cached AI response");
                    return cachedResponse;
                }

                // Generate prompt
                String prompt = buildPrompt(codeContext, currentLine);

                // Make API call based on configured provider
                AIProvider provider = getConfiguredProvider();
                String response;

                if (provider == AIProvider.OPENAI) {
                    response = callOpenAI(prompt);
                } else {
                    response = callClaude(prompt);
                }

                // Cache successful response
                if (response != null && !response.trim().isEmpty()) {
                    responseCache.put(cacheKey, response);

                    // Limit cache size to prevent memory issues
                    if (responseCache.size() > 100) {
                        responseCache.clear();
                    }
                }

                return response;

            } catch (Exception e) {
                LOG.warn("AI completion request failed", e);
                return null;
            }
        }, aiExecutor);
    }

    /**
     * Builds an effective prompt for code completion
     */
    private String buildPrompt(String codeContext, String currentLine) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Complete the following code. Provide only the completion, no explanations:\n\n");
        prompt.append("Context:\n");
        prompt.append(codeContext);
        prompt.append("\n\nCurrent line to complete:\n");
        prompt.append(currentLine);
        prompt.append("\n\nCompletion:");

        return prompt.toString();
    }

    /**
     * Makes API call to OpenAI GPT
     */
    private String callOpenAI(String prompt) throws IOException, InterruptedException {
        String apiKey = System.getenv("OPENAI_API_KEY");

        System.out.println("🔑 API Key present: " + (apiKey != null && !apiKey.isEmpty()));
        System.out.println("📝 Prompt length: " + prompt.length());
        System.out.println("📝 Prompt preview: " + prompt.substring(0, Math.min(100, prompt.length())));

        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.out.println("❌ API key is null or empty");
            LOG.warn("OPENAI_API_KEY environment variable not set");
            return null;
        }

        String escapedPrompt = escapeJson(prompt);
        String requestBody = "{\n" +
                "    \"model\": \"gpt-3.5-turbo\",\n" +
                "    \"messages\": [\n" +
                "        {\n" +
                "            \"role\": \"system\",\n" +
                "            \"content\": \"You are a code completion assistant. Provide clean, accurate code completions without explanations.\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"role\": \"user\",\n" +
                "            \"content\": \"" + escapedPrompt + "\"\n" +
                "        }\n" +
                "    ],\n" +
                "    \"max_tokens\": 100,\n" +
                "    \"temperature\": 0.1,\n" +
                "    \"stream\": false\n" +
                "}";

        System.out.println("🌐 Making API request to OpenAI...");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("📡 Response status: " + response.statusCode());
        System.out.println("📡 Response body preview: " + response.body().substring(0, Math.min(200, response.body().length())));

        if (response.statusCode() == 200) {
            String parsed = parseOpenAIResponse(response.body());
            System.out.println("✅ Parsed response: " + (parsed != null ? parsed.substring(0, Math.min(50, parsed.length())) : "NULL"));
            return parsed;
        } else {
            System.out.println("❌ API Error: " + response.statusCode() + " - " + response.body());
            LOG.warn("OpenAI API error: " + response.statusCode() + " - " + response.body());
            return null;
        }
    }

    /**
     * Makes API call to Anthropic Claude
     */
    private String callClaude(String prompt) throws IOException, InterruptedException {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");

        if (apiKey == null || apiKey.trim().isEmpty()) {
            LOG.warn("ANTHROPIC_API_KEY environment variable not set");
            return null;
        }

        String escapedPrompt = escapeJson(prompt);
        String requestBody = "{\n" +
                "    \"model\": \"claude-3-sonnet-20240229\",\n" +
                "    \"max_tokens\": 150,\n" +
                "    \"messages\": [\n" +
                "        {\n" +
                "            \"role\": \"user\",\n" +
                "            \"content\": \"" + escapedPrompt + "\"\n" +
                "        }\n" +
                "    ]\n" +
                "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CLAUDE_API_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return parseClaudeResponse(response.body());
        } else {
            LOG.warn("Claude API error: " + response.statusCode() + " - " + response.body());
            return null;
        }
    }

    /**
     * Parses OpenAI API response to extract completion text - BULLETPROOF VERSION
     */
    private String parseOpenAIResponse(String responseBody) {
        try {
            System.out.println("🔍 Parsing OpenAI response...");

            // Method 1: Look for "content": " (with space)
            int contentStart = responseBody.indexOf("\"content\": \"");
            if (contentStart != -1) {
                contentStart += 12; // Move past "content": "
            } else {
                // Method 2: Look for "content":" (without space)
                contentStart = responseBody.indexOf("\"content\":\"");
                if (contentStart != -1) {
                    contentStart += 11; // Move past "content":"
                } else {
                    System.out.println("❌ No content field found");
                    return null;
                }
            }

            // Find the end quote, handling escaped quotes
            int contentEnd = contentStart;
            boolean escaped = false;

            while (contentEnd < responseBody.length()) {
                char c = responseBody.charAt(contentEnd);

                if (!escaped && c == '"') {
                    // Found the end quote
                    break;
                }

                // Update escaped state
                escaped = (c == '\\' && !escaped);
                contentEnd++;
            }

            if (contentEnd <= contentStart) {
                System.out.println("❌ Could not find content end");
                return null;
            }

            String rawContent = responseBody.substring(contentStart, contentEnd);
            System.out.println("🎉 Raw extracted: " + rawContent.substring(0, Math.min(100, rawContent.length())));

            // Unescape the JSON
            String finalContent = unescapeJson(rawContent);
            System.out.println("🎉 Final completion: " + finalContent.substring(0, Math.min(100, finalContent.length())));

            return finalContent;

        } catch (Exception e) {
            System.out.println("❌ Parser failed: " + e.getMessage());
            e.printStackTrace();

            // EMERGENCY FALLBACK: Manual extraction from your example
            try {
                // We know from your log the content is there, extract it manually
                String marker = "\"content\": \"";
                int start = responseBody.indexOf(marker);
                if (start != -1) {
                    start += marker.length();
                    int end = start;

                    // Find end quote
                    while (end < responseBody.length() && responseBody.charAt(end) != '"') {
                        if (responseBody.charAt(end) == '\\') {
                            end++; // Skip escaped character
                        }
                        end++;
                    }

                    if (end > start) {
                        String emergency = responseBody.substring(start, end);
                        System.out.println("🚨 Emergency fallback worked: " + emergency.substring(0, Math.min(50, emergency.length())));
                        return unescapeJson(emergency);
                    }
                }
            } catch (Exception ex) {
                System.out.println("❌ Emergency fallback failed: " + ex.getMessage());
            }
        }

        return null;
    }

    /**
     * Parses Claude API response to extract completion text
     */
    private String parseClaudeResponse(String responseBody) {
        try {
            // Simple JSON parsing for Claude response format
            int textStart = responseBody.indexOf("\"text\":\"") + 8;
            int textEnd = responseBody.indexOf("\"", textStart);

            if (textStart > 7 && textEnd > textStart) {
                String content = responseBody.substring(textStart, textEnd);
                return unescapeJson(content);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse Claude response", e);
        }
        return null;
    }

    /**
     * Simple JSON escaping for request bodies
     */
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Simple JSON unescaping for responses
     */
    private String unescapeJson(String text) {
        return text.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    /**
     * Generates cache key for request caching
     */
    private String generateCacheKey(String codeContext, String currentLine) {
        // Simple hash-based cache key
        return String.valueOf((codeContext + currentLine).hashCode());
    }

    /**
     * Gets configured AI provider from environment variable
     */
    private AIProvider getConfiguredProvider() {
        String provider = System.getenv("AI_PROVIDER");
        if ("CLAUDE".equalsIgnoreCase(provider)) {
            return AIProvider.CLAUDE;
        }
        return AIProvider.OPENAI; // Default
    }

    /**
     * Checks if AI service is available and configured
     */
    public boolean isAvailable() {
        AIProvider provider = getConfiguredProvider();

        if (provider == AIProvider.OPENAI) {
            String apiKey = System.getenv("OPENAI_API_KEY");
            return apiKey != null && !apiKey.trim().isEmpty();
        } else {
            String apiKey = System.getenv("ANTHROPIC_API_KEY");
            return apiKey != null && !apiKey.trim().isEmpty();
        }
    }

    /**
     * Clears response cache
     */
    public void clearCache() {
        responseCache.clear();
        LOG.info("AI response cache cleared");
    }

    /**
     * Available AI providers
     */
    public enum AIProvider {
        OPENAI,
        CLAUDE
    }
}