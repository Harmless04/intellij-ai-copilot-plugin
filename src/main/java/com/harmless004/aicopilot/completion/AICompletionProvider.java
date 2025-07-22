package com.harmless004.aicopilot.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import com.harmless004.aicopilot.services.AIService;
import com.harmless004.aicopilot.services.CodeContextAnalyzer;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Main completion provider that integrates AI-powered code suggestions
 * into IntelliJ's completion system.
 */
public class AICompletionProvider extends CompletionProvider<CompletionParameters> {

    private static final Logger LOG = Logger.getInstance(AICompletionProvider.class);
    private static final int MAX_COMPLETION_TIME_MS = 3000; // 3 second timeout
    private static final int MIN_TRIGGER_LENGTH = 3; // Minimum characters to trigger

    private final AIService aiService;
    private final CodeContextAnalyzer contextAnalyzer;

    public AICompletionProvider() {
        this.aiService = ApplicationManager.getApplication().getService(AIService.class);
        this.contextAnalyzer = new CodeContextAnalyzer();
    }

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {

        // Early exit conditions
        if (!shouldTriggerCompletion(parameters)) {
            return;
        }

        try {
            // Extract context information
            PsiFile file = parameters.getOriginalFile();
            Editor editor = parameters.getEditor();
            int offset = parameters.getOffset();

            String codeContext = contextAnalyzer.extractContext(file, editor, offset);
            String currentLine = getCurrentLine(editor, offset);

            // Log for debugging
            LOG.info("AI Completion triggered for context: " + codeContext.substring(0, Math.min(100, codeContext.length())));

            // Make async AI request
            CompletableFuture<String> aiResponse = aiService.getCompletion(codeContext, currentLine);

            // Handle response with timeout
            try {
                String suggestion = aiResponse.get(MAX_COMPLETION_TIME_MS, TimeUnit.MILLISECONDS);

                if (suggestion != null && !suggestion.trim().isEmpty()) {
                    addAISuggestion(result, suggestion, parameters);
                }

            } catch (Exception e) {
                LOG.warn("AI completion request failed or timed out", e);
                // Gracefully handle timeout - don't block user
            }

        } catch (Exception e) {
            LOG.error("Error in AI completion provider", e);
        }
    }

    /**
     * Determines if AI completion should be triggered based on context
     */
    private boolean shouldTriggerCompletion(CompletionParameters parameters) {
        // Check if AI service is available
        if (!aiService.isAvailable()) {
            return false;
        }

        PsiElement element = parameters.getPosition();
        PsiFile file = parameters.getOriginalFile();

        // Only trigger for supported file types
        if (!isSupportedFileType(file)) {
            return false;
        }

        // Check if we're in a comment (common trigger scenario)
        if (isInComment(element)) {
            return true;
        }

        // Check if we're in incomplete code
        if (isIncompleteCode(parameters)) {
            return true;
        }

        // Check minimum length requirement
        String currentLine = getCurrentLine(parameters.getEditor(), parameters.getOffset());
        return currentLine.trim().length() >= MIN_TRIGGER_LENGTH;
    }

    /**
     * Checks if the file type is supported for AI completion
     */
    private boolean isSupportedFileType(PsiFile file) {
        if (file.getVirtualFile() == null) return false;

        String extension = file.getVirtualFile().getExtension();
        return extension != null && (
                extension.equals("java") ||
                        extension.equals("py") ||
                        extension.equals("js") ||
                        extension.equals("ts") ||
                        extension.equals("kt") ||
                        extension.equals("scala")
        );
    }

    /**
     * Checks if cursor is in a comment
     */
    private boolean isInComment(PsiElement element) {
        // This would need more sophisticated PSI analysis
        String elementText = element.getText();
        return elementText.contains("//") || elementText.contains("/*") || elementText.contains("#");
    }

    /**
     * Detects incomplete code patterns that should trigger completion
     */
    private boolean isIncompleteCode(CompletionParameters parameters) {
        String currentLine = getCurrentLine(parameters.getEditor(), parameters.getOffset());

        // Common incomplete patterns
        return currentLine.trim().endsWith(":") ||     // Python function/class definitions
                currentLine.trim().endsWith("{") ||     // Java/JS block starts
                currentLine.contains("def ") ||         // Python function definitions
                currentLine.contains("function ") ||    // JavaScript functions
                currentLine.contains("public ") ||      // Java method starts
                currentLine.contains("private ") ||
                currentLine.contains("protected ");
    }

    /**
     * Gets the current line text at the cursor position
     */
    private String getCurrentLine(Editor editor, int offset) {
        Document document = editor.getDocument();
        int lineNumber = document.getLineNumber(offset);
        int lineStartOffset = document.getLineStartOffset(lineNumber);
        int lineEndOffset = document.getLineEndOffset(lineNumber);

        return document.getText(new TextRange(lineStartOffset, lineEndOffset));
    }

    /**
     * Adds AI suggestion to completion results
     */
    private void addAISuggestion(CompletionResultSet result, String suggestion, CompletionParameters parameters) {
        // Clean and format the suggestion
        String cleanSuggestion = cleanAISuggestion(suggestion);

        if (cleanSuggestion.isEmpty()) {
            return;
        }

        // Create lookup element with AI icon and priority
        LookupElement lookupElement = LookupElementBuilder
                .create(cleanSuggestion)
                .withPresentableText("✨ " + truncateForDisplay(cleanSuggestion))
                .withTypeText("AI Suggestion")
                .withBoldness(true)
                .withInsertHandler((context, item) -> {
                    // Custom insert handler for multi-line suggestions
                    handleAIInsert(context, cleanSuggestion);
                });

        // Add with high priority so it appears at top
        result.withPrefixMatcher("").addElement(PrioritizedLookupElement.withPriority(lookupElement, 1000));
    }

    /**
     * Cleans and formats AI suggestion for insertion
     */
    private String cleanAISuggestion(String suggestion) {
        // Remove markdown code blocks if present
        suggestion = suggestion.replaceAll("```\\w*\\n?", "").replaceAll("```", "");

        // Remove excessive whitespace
        suggestion = suggestion.trim();

        // Ensure proper indentation (would need more sophisticated logic)
        return suggestion;
    }

    /**
     * Truncates suggestion text for display in completion popup
     */
    private String truncateForDisplay(String suggestion) {
        if (suggestion.length() <= 50) {
            return suggestion;
        }

        // Show first line or first 50 characters
        String firstLine = suggestion.split("\n")[0];
        if (firstLine.length() <= 50) {
            return firstLine + "...";
        }

        return suggestion.substring(0, 47) + "...";
    }

    /**
     * Handles insertion of AI suggestions, including multi-line code
     */
    private void handleAIInsert(InsertionContext context, String suggestion) {
        Document document = context.getDocument();
        int startOffset = context.getStartOffset();
        int endOffset = context.getTailOffset();

        // Replace the completion text
        document.replaceString(startOffset, endOffset, suggestion);

        // Move cursor to end of insertion
        context.getEditor().getCaretModel().moveToOffset(startOffset + suggestion.length());

        LOG.info("AI suggestion inserted: " + suggestion.substring(0, Math.min(50, suggestion.length())));
    }
}