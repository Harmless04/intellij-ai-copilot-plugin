package com.harmless004.aicopilot.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
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
        System.out.println("🚀 AI Copilot action triggered!");

        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null || editor == null || psiFile == null) {
            System.out.println("❌ Missing project, editor, or file");
            return;
        }

        System.out.println("✅ Project, editor, and file available");

        // Check if AI service is available
        AIService aiService = ApplicationManager.getApplication().getService(AIService.class);
        if (!aiService.isAvailable()) {
            System.out.println("❌ AI Service not available");
            showConfigurationMessage(project);
            return;
        }

        System.out.println("✅ AI Service is available");

        // Show immediate feedback to user
        showInfoMessage(project, "🤖 AI Copilot is generating completion...");

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
        ApplicationManager.getApplication().runReadAction(() -> {
            try {
                int offset = editor.getCaretModel().getOffset();
                String currentLine = getCurrentLine(editor, offset);

                CodeContextAnalyzer analyzer = new CodeContextAnalyzer();
                String context = analyzer.extractContext(psiFile, editor, offset);

                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    try {
                        AIService aiService = ApplicationManager.getApplication().getService(AIService.class);
                        aiService.getCompletion(context, currentLine)
                                .thenAccept(suggestion -> {
                                    System.out.println("🤖 AI RESPONSE RECEIVED: " + (suggestion != null ? suggestion.substring(0, Math.min(50, suggestion.length())) : "NULL"));

                                    if (suggestion != null && !suggestion.trim().isEmpty()) {
                                        ApplicationManager.getApplication().invokeLater(() -> {
                                            WriteCommandAction.runWriteCommandAction(project, () -> {
                                                System.out.println("🚀 INSERTING AI SUGGESTION INTO FILE");
                                                Document document = editor.getDocument();
                                                document.insertString(offset, suggestion);
                                                showInfoMessage(project, "✅ AI completion inserted!");
                                            });
                                        });
                                    } else {
                                        System.out.println("❌ NO SUGGESTION RECEIVED");
                                        ApplicationManager.getApplication().invokeLater(() -> {
                                            showInfoMessage(project, "❌ No AI suggestion available for this context.");
                                        });
                                    }
                                })
                                .exceptionally(throwable -> {
                                    ApplicationManager.getApplication().invokeLater(() -> {
                                        showErrorMessage(project, "AI completion failed: " + throwable.getMessage());
                                    });
                                    return null;
                                });

                    } catch (Exception ex) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            showErrorMessage(project, "Error in AI processing: " + ex.getMessage());
                        });
                    }
                });

            } catch (Exception ex) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    showErrorMessage(project, "Error extracting context: " + ex.getMessage());
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

    private void showConfigurationMessage(Project project) {
        ApplicationManager.getApplication().invokeLater(() -> {
            showErrorMessage(project, "AI Copilot is not configured. Please set OPENAI_API_KEY or ANTHROPIC_API_KEY environment variable.");
        });
    }

    private void showErrorMessage(Project project, String message) {
        System.err.println("AI Copilot Error: " + message);
    }

    private void showInfoMessage(Project project, String message) {
        System.out.println("AI Copilot Info: " + message);
    }
}
