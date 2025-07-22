package com.harmless004.aicopilot.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.harmless004.aicopilot.services.AIService;
import com.harmless004.aicopilot.services.CodeContextAnalyzer;
import org.jetbrains.annotations.NotNull;

/**
 * Action to manually trigger AI completion when the user presses Ctrl+Alt+Space
 */
public class TriggerCompletionAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null || editor == null || psiFile == null) {
            return;
        }

        // Check if AI service is available
        AIService aiService = ApplicationManager.getApplication().getService(AIService.class);
        if (!aiService.isAvailable()) {
            // Show notification that API key is not configured
            showConfigurationMessage(project);
            return;
        }

        // Trigger AI completion at current cursor position
        triggerAICompletion(project, editor, psiFile);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Enable action only when editor is available
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        boolean enabled = project != null && editor != null;
        e.getPresentation().setEnabledAndVisible(enabled);
    }

    private void triggerAICompletion(Project project, Editor editor, PsiFile psiFile) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                int offset = editor.getCaretModel().getOffset();

                // Extract context
                CodeContextAnalyzer analyzer = new CodeContextAnalyzer();
                String context = analyzer.extractContext(psiFile, editor, offset);

                // Get current line
                String currentLine = getCurrentLine(editor, offset);

                // Get AI completion
                AIService aiService = ApplicationManager.getApplication().getService(AIService.class);
                aiService.getCompletion(context, currentLine)
                        .thenAccept(suggestion -> {
                            if (suggestion != null && !suggestion.trim().isEmpty()) {
                                // Insert suggestion at cursor position
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    insertSuggestion(editor, suggestion, offset);
                                });
                            }
                        })
                        .exceptionally(throwable -> {
                            // Handle error
                            ApplicationManager.getApplication().invokeLater(() -> {
                                showErrorMessage(project, "AI completion failed: " + throwable.getMessage());
                            });
                            return null;
                        });

            } catch (Exception ex) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    showErrorMessage(project, "Error triggering AI completion: " + ex.getMessage());
                });
            }
        });
    }

    private String getCurrentLine(Editor editor, int offset) {
        Document document = editor.getDocument();
        int lineNumber = document.getLineNumber(offset);
        int lineStartOffset = document.getLineStartOffset(lineNumber);
        int lineEndOffset = document.getLineEndOffset(lineNumber);

        return document.getText(new TextRange(lineStartOffset, lineEndOffset));
    }

    private void insertSuggestion(Editor editor, String suggestion, int offset) {
        ApplicationManager.getApplication().runWriteAction(() -> {
            editor.getDocument().insertString(offset, suggestion);
            editor.getCaretModel().moveToOffset(offset + suggestion.length());
        });
    }

    private void showConfigurationMessage(Project project) {
        ApplicationManager.getApplication().invokeLater(() -> {
            showErrorMessage(project, "AI Copilot is not configured. Please set OPENAI_API_KEY or ANTHROPIC_API_KEY environment variable.");
        });
    }

    private void showErrorMessage(Project project, String message) {
        // Simple error notification - in a real implementation, use IntelliJ's notification system
        System.err.println("AI Copilot Error: " + message);

        // You could show a balloon notification here:
        // NotificationGroupManager.getInstance()
        //     .getNotificationGroup("AI Copilot")
        //     .createNotification(message, NotificationType.ERROR)
        //     .notify(project);
    }
}