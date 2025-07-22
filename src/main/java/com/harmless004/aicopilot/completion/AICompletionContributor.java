package com.harmless004.aicopilot.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * Main completion contributor that registers AI completion providers
 * for different contexts and programming languages.
 */
public class AICompletionContributor extends CompletionContributor {

    public AICompletionContributor() {
        registerCompletionProviders();
    }

    private void registerCompletionProviders() {
        AICompletionProvider aiProvider = new AICompletionProvider();

        // Register for comments (comment-to-code completion)
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().inside(PsiComment.class),
                aiProvider);

        // Register for general code completion
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement()
                        .andNot(PlatformPatterns.psiElement().inside(PsiComment.class))
                        .andNot(PlatformPatterns.psiElement(PsiWhiteSpace.class)),
                aiProvider);

        // Register for smart completion (triggered with Ctrl+Shift+Space)
        extend(CompletionType.SMART,
                PlatformPatterns.psiElement(),
                aiProvider);
    }

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {

        // Check if AI completion is enabled (basic check)
        String aiProvider = System.getenv("AI_PROVIDER");
        if (aiProvider == null) {
            // Default behavior - still try completion if API keys are available
            String openaiKey = System.getenv("OPENAI_API_KEY");
            String claudeKey = System.getenv("ANTHROPIC_API_KEY");

            if (openaiKey == null && claudeKey == null) {
                return; // No API keys configured
            }
        }

        // Check specific completion types
        PsiElement element = parameters.getPosition();

        // Always allow comment completion as it's the main use case
        if (element.getParent() instanceof PsiComment) {
            super.fillCompletionVariants(parameters, result);
            return;
        }

        // For regular code completion, we can add more sophisticated filtering here
        super.fillCompletionVariants(parameters, result);
    }

    @Override
    public void beforeCompletion(@NotNull CompletionInitializationContext context) {
        // Customize completion behavior before it starts
        super.beforeCompletion(context);

        // You can modify the dummy identifier or other context here
        // For example, prevent IntelliJ from adding its dummy identifier in certain cases
    }
}