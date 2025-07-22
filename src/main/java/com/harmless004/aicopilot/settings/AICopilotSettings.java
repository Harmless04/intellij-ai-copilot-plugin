
package com.harmless004.aicopilot.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.harmless004.aicopilot.services.AIService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent settings for the AI Copilot plugin
 */
@Service
@State(
        name = "AICopilotSettings",
        storages = @Storage("aiCopilotSettings.xml")
)
public final class AICopilotSettings implements PersistentStateComponent<AICopilotSettings> {

    // AI Provider Settings
    public AIService.AIProvider aiProvider = AIService.AIProvider.OPENAI;
    public String openAIApiKey = "";
    public String claudeApiKey = "";

    // Completion Settings
    public boolean enableAutoCompletion = true;
    public boolean enableCommentCompletion = true;
    public boolean enableCodeCompletion = true;
    public int completionDelay = 300; // milliseconds
    public int maxCompletionLength = 150; // tokens

    // UI Settings
    public boolean showInlinePreview = true;
    public boolean showAIBadge = true;
    public boolean enableSuggestionSounds = false;

    // Performance Settings
    public boolean enableCaching = true;
    public int cacheSize = 100;
    public int requestTimeout = 5000; // milliseconds

    // Privacy Settings
    public boolean sendFileContext = true;
    public boolean sendProjectContext = false;
    public boolean logRequests = false;

    public static AICopilotSettings getInstance() {
        return ApplicationManager.getApplication().getService(AICopilotSettings.class);
    }

    @Nullable
    @Override
    public AICopilotSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull AICopilotSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    // Getters and setters
    public AIService.AIProvider getAIProvider() {
        return aiProvider;
    }

    public void setAIProvider(AIService.AIProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    public String getOpenAIApiKey() {
        return openAIApiKey;
    }

    public void setOpenAIApiKey(String openAIApiKey) {
        this.openAIApiKey = openAIApiKey;
    }

    public String getClaudeApiKey() {
        return claudeApiKey;
    }

    public void setClaudeApiKey(String claudeApiKey) {
        this.claudeApiKey = claudeApiKey;
    }

    public boolean isEnableAutoCompletion() {
        return enableAutoCompletion;
    }

    public void setEnableAutoCompletion(boolean enableAutoCompletion) {
        this.enableAutoCompletion = enableAutoCompletion;
    }

    public boolean isEnableCommentCompletion() {
        return enableCommentCompletion;
    }

    public void setEnableCommentCompletion(boolean enableCommentCompletion) {
        this.enableCommentCompletion = enableCommentCompletion;
    }

    public boolean isEnableCodeCompletion() {
        return enableCodeCompletion;
    }

    public void setEnableCodeCompletion(boolean enableCodeCompletion) {
        this.enableCodeCompletion = enableCodeCompletion;
    }

    public int getCompletionDelay() {
        return completionDelay;
    }

    public void setCompletionDelay(int completionDelay) {
        this.completionDelay = completionDelay;
    }

    public int getMaxCompletionLength() {
        return maxCompletionLength;
    }

    public void setMaxCompletionLength(int maxCompletionLength) {
        this.maxCompletionLength = maxCompletionLength;
    }

    public boolean isShowInlinePreview() {
        return showInlinePreview;
    }

    public void setShowInlinePreview(boolean showInlinePreview) {
        this.showInlinePreview = showInlinePreview;
    }

    public boolean isShowAIBadge() {
        return showAIBadge;
    }

    public void setShowAIBadge(boolean showAIBadge) {
        this.showAIBadge = showAIBadge;
    }

    public boolean isEnableSuggestionSounds() {
        return enableSuggestionSounds;
    }

    public void setEnableSuggestionSounds(boolean enableSuggestionSounds) {
        this.enableSuggestionSounds = enableSuggestionSounds;
    }

    public boolean isEnableCaching() {
        return enableCaching;
    }

    public void setEnableCaching(boolean enableCaching) {
        this.enableCaching = enableCaching;
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(int requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public boolean isSendFileContext() {
        return sendFileContext;
    }

    public void setSendFileContext(boolean sendFileContext) {
        this.sendFileContext = sendFileContext;
    }

    public boolean isSendProjectContext() {
        return sendProjectContext;
    }

    public void setSendProjectContext(boolean sendProjectContext) {
        this.sendProjectContext = sendProjectContext;
    }

    public boolean isLogRequests() {
        return logRequests;
    }

    public void setLogRequests(boolean logRequests) {
        this.logRequests = logRequests;
    }

    /**
     * Validates current settings
     */
    public boolean isConfigurationValid() {
        return switch (aiProvider) {
            case OPENAI -> openAIApiKey != null && !openAIApiKey.trim().isEmpty();
            case CLAUDE -> claudeApiKey != null && !claudeApiKey.trim().isEmpty();
        };
    }

    /**
     * Resets settings to defaults
     */
    public void resetToDefaults() {
        aiProvider = AIService.AIProvider.OPENAI;
        openAIApiKey = "";
        claudeApiKey = "";
        enableAutoCompletion = true;
        enableCommentCompletion = true;
        enableCodeCompletion = true;
        completionDelay = 300;
        maxCompletionLength = 150;
        showInlinePreview = true;
        showAIBadge = true;
        enableSuggestionSounds = false;
        enableCaching = true;
        cacheSize = 100;
        requestTimeout = 5000;
        sendFileContext = true;
        sendProjectContext = false;
        logRequests = false;
    }
}