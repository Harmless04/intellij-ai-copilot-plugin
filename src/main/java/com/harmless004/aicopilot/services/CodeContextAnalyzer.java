package com.harmless004.aicopilot.services;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Analyzes code context around the cursor position to provide
 * relevant information for AI completion requests.
 * Works properly with all file types and provides meaningful context.
 */
public class CodeContextAnalyzer {

    private static final int MAX_CONTEXT_LENGTH = 2000;
    private static final int LINES_BEFORE_CURSOR = 15;
    private static final int LINES_AFTER_CURSOR = 5;

    /**
     * Extracts comprehensive context from the current file and cursor position
     */
    public String extractContext(@NotNull PsiFile file, @NotNull Editor editor, int offset) {
        StringBuilder context = new StringBuilder();

        // 1. File information
        context.append("File: ").append(file.getName()).append("\n");
        context.append("Language: ").append(file.getFileType().getName()).append("\n\n");

        // 2. Imports and dependencies
        String imports = extractImportsAndPackages(file);
        if (!imports.isEmpty()) {
            context.append("Dependencies:\n").append(imports).append("\n\n");
        }

        // 3. Current class/function context
        String structureContext = extractStructureContext(file, offset);
        if (!structureContext.isEmpty()) {
            context.append("Structure Context:\n").append(structureContext).append("\n\n");
        }

        // 4. Surrounding code with proper context
        String codeContext = extractSurroundingCode(editor, offset);
        context.append("Code Context:\n").append(codeContext);

        // Limit and clean up context
        String result = context.toString();
        if (result.length() > MAX_CONTEXT_LENGTH) {
            result = result.substring(0, MAX_CONTEXT_LENGTH) + "\n... (truncated for brevity)";
        }

        return result;
    }

    /**
     * Extracts imports, packages, and dependencies - works for multiple languages
     */
    private String extractImportsAndPackages(@NotNull PsiFile file) {
        List<String> dependencies = new ArrayList<>();
        String fileText = file.getText();
        String[] lines = fileText.split("\n");

        int lineCount = 0;
        for (String line : lines) {
            lineCount++;
            String trimmed = line.trim();

            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                continue;
            }

            // Java/Kotlin/Scala imports and package
            if (trimmed.startsWith("package ") || trimmed.startsWith("import ")) {
                dependencies.add(trimmed);
            }
            // Python imports
            else if (trimmed.startsWith("from ") || (trimmed.startsWith("import ") && !trimmed.contains("java"))) {
                dependencies.add(trimmed);
            }
            // JavaScript/TypeScript imports and requires
            else if (trimmed.startsWith("import ") || trimmed.startsWith("const ") && trimmed.contains("require") ||
                    trimmed.startsWith("let ") && trimmed.contains("require")) {
                dependencies.add(trimmed);
            }

            // Stop after 50 lines or 20 imports to avoid processing entire file
            if (lineCount > 50 || dependencies.size() > 20) {
                break;
            }
        }

        return dependencies.stream()
                .distinct()
                .limit(15)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Extracts structural context - classes, methods, functions around cursor
     */
    private String extractStructureContext(@NotNull PsiFile file, int offset) {
        List<String> structure = new ArrayList<>();

        try {
            PsiElement element = file.findElementAt(offset);
            if (element != null) {

                // Find containing structures using PSI tree walking
                PsiElement current = element;
                while (current != null && structure.size() < 5) {
                    String elementInfo = getElementStructureInfo(current);
                    if (elementInfo != null && !elementInfo.isEmpty()) {
                        structure.add(elementInfo);
                    }
                    current = current.getParent();
                }
            }
        } catch (Exception e) {
            // Fallback to text-based analysis if PSI fails
            return extractStructureFromText(file, offset);
        }

        if (structure.isEmpty()) {
            return extractStructureFromText(file, offset);
        }

        return structure.stream()
                .distinct()
                .collect(Collectors.joining("\n"));
    }

    /**
     * Get structure information from PSI element
     */
    private String getElementStructureInfo(PsiElement element) {
        if (element == null) return null;

        String elementType = element.getClass().getSimpleName();
        String elementText = element.getText();

        // Handle different types of elements
        if (elementType.contains("Class")) {
            return extractClassInfo(element);
        } else if (elementType.contains("Method") || elementType.contains("Function")) {
            return extractMethodInfo(element);
        } else if (elementType.contains("Field") || elementType.contains("Variable")) {
            return extractFieldInfo(element);
        }

        return null;
    }

    /**
     * Extract class information
     */
    private String extractClassInfo(PsiElement element) {
        try {
            String text = element.getText();
            String[] lines = text.split("\n");
            String firstLine = lines[0].trim();

            // Clean up class declaration
            if (firstLine.contains("{")) {
                firstLine = firstLine.substring(0, firstLine.indexOf("{")).trim();
            }

            return "Class: " + firstLine;
        } catch (Exception e) {
            return "Class: " + element.toString().substring(0, Math.min(50, element.toString().length()));
        }
    }

    /**
     * Extract method/function information
     */
    private String extractMethodInfo(PsiElement element) {
        try {
            String text = element.getText();
            String[] lines = text.split("\n");
            String signature = lines[0].trim();

            // Clean up method signature
            if (signature.contains("{")) {
                signature = signature.substring(0, signature.indexOf("{")).trim();
            }

            return "Method: " + signature;
        } catch (Exception e) {
            return "Method: " + element.toString().substring(0, Math.min(60, element.toString().length()));
        }
    }

    /**
     * Extract field/variable information
     */
    private String extractFieldInfo(PsiElement element) {
        try {
            String text = element.getText().trim();
            if (text.length() > 80) {
                text = text.substring(0, 80) + "...";
            }
            return "Field: " + text;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fallback text-based structure extraction
     */
    private String extractStructureFromText(@NotNull PsiFile file, int offset) {
        List<String> structure = new ArrayList<>();
        String fileText = file.getText();
        String[] lines = fileText.split("\n");

        Document doc = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (doc == null) return "";

        int currentLine = doc.getLineNumber(offset);

        // Look backwards for class/method declarations
        for (int i = currentLine; i >= 0 && i >= currentLine - 20; i--) {
            if (i < lines.length) {
                String line = lines[i].trim();

                if (isClassDeclaration(line)) {
                    structure.add("Class: " + cleanDeclaration(line));
                } else if (isMethodDeclaration(line)) {
                    structure.add("Method: " + cleanDeclaration(line));
                } else if (isFunctionDeclaration(line)) {
                    structure.add("Function: " + cleanDeclaration(line));
                }
            }
        }

        return structure.stream().collect(Collectors.joining("\n"));
    }

    /**
     * Check if line is a class declaration
     */
    private boolean isClassDeclaration(String line) {
        return line.contains("class ") && !line.startsWith("//") && !line.startsWith("*");
    }

    /**
     * Check if line is a method declaration
     */
    private boolean isMethodDeclaration(String line) {
        return (line.contains("public ") || line.contains("private ") || line.contains("protected ")) &&
                line.contains("(") && !line.startsWith("//");
    }

    /**
     * Check if line is a function declaration
     */
    private boolean isFunctionDeclaration(String line) {
        return (line.startsWith("def ") || line.contains("function ")) &&
                line.contains("(") && !line.startsWith("//");
    }

    /**
     * Clean up declaration for display
     */
    private String cleanDeclaration(String line) {
        if (line.contains("{")) {
            line = line.substring(0, line.indexOf("{")).trim();
        }
        if (line.contains(":")) {
            // Python function - keep the colon
            if (line.trim().startsWith("def ")) {
                return line;
            }
            line = line.substring(0, line.indexOf(":")).trim();
        }
        return line.length() > 100 ? line.substring(0, 100) + "..." : line;
    }

    /**
     * Extracts code around cursor with intelligent context
     */
    private String extractSurroundingCode(@NotNull Editor editor, int offset) {
        Document document = editor.getDocument();
        int currentLineNum = document.getLineNumber(offset);
        int totalLines = document.getLineCount();

        // Calculate intelligent range based on file size
        int beforeLines = Math.min(LINES_BEFORE_CURSOR, currentLineNum);
        int afterLines = Math.min(LINES_AFTER_CURSOR, totalLines - currentLineNum - 1);

        int startLine = currentLineNum - beforeLines;
        int endLine = currentLineNum + afterLines;

        StringBuilder code = new StringBuilder();

        for (int i = startLine; i <= endLine; i++) {
            if (i >= 0 && i < totalLines) {
                int lineStart = document.getLineStartOffset(i);
                int lineEnd = document.getLineEndOffset(i);
                String lineText = document.getText(new TextRange(lineStart, lineEnd));

                // Add line numbers for context
                String lineNumber = String.format("%3d: ", i + 1);

                if (i == currentLineNum) {
                    // Highlight current line
                    int cursorPos = offset - lineStart;
                    String beforeCursor = lineText.substring(0, Math.min(cursorPos, lineText.length()));
                    String afterCursor = lineText.substring(Math.min(cursorPos, lineText.length()));
                    code.append(lineNumber).append(beforeCursor).append("█").append(afterCursor).append("\n");
                } else {
                    code.append(lineNumber).append(lineText).append("\n");
                }
            }
        }

        return code.toString();
    }

    /**
     * Analyzes current context for completion hints
     */
    public CodeContext analyzeCurrentContext(@NotNull PsiFile file, int offset) {
        String currentLine = getCurrentLineText(file, offset);

        boolean inComment = isInComment(file, offset);
        boolean inMethod = isInMethod(file, offset);
        boolean inClass = isInClass(file, offset);

        CompletionContext context = determineCompletionContext(currentLine, file, offset);

        return new CodeContext(inMethod, inClass, inComment, context, currentLine);
    }

    /**
     * Check if cursor is in a comment
     */
    private boolean isInComment(@NotNull PsiFile file, int offset) {
        try {
            PsiElement element = file.findElementAt(offset);
            while (element != null) {
                if (element instanceof PsiComment ||
                        element.getClass().getSimpleName().toLowerCase().contains("comment")) {
                    return true;
                }
                element = element.getParent();
            }
        } catch (Exception e) {
            // Text-based fallback
            String line = getCurrentLineText(file, offset).trim();
            return line.startsWith("//") || line.startsWith("/*") || line.startsWith("#");
        }
        return false;
    }

    /**
     * Check if cursor is in a method
     */
    private boolean isInMethod(@NotNull PsiFile file, int offset) {
        String textBefore = file.getText().substring(0, Math.min(offset, file.getText().length()));

        // Count braces to determine if we're inside a method
        int openBraces = 0;
        boolean foundMethodSignature = false;

        String[] lines = textBefore.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();

            // Look for method signatures
            if (!trimmed.startsWith("//") && (
                    (trimmed.contains("public ") || trimmed.contains("private ") ||
                            trimmed.contains("protected ")) && trimmed.contains("(") ||
                            trimmed.startsWith("def ") && trimmed.contains("(") ||
                            trimmed.contains("function ") && trimmed.contains("("))) {
                foundMethodSignature = true;
            }

            // Count braces
            for (char c : line.toCharArray()) {
                if (c == '{') openBraces++;
                else if (c == '}') openBraces--;
            }
        }

        return foundMethodSignature && openBraces > 0;
    }

    /**
     * Check if cursor is in a class
     */
    private boolean isInClass(@NotNull PsiFile file, int offset) {
        String textBefore = file.getText().substring(0, Math.min(offset, file.getText().length()));
        return textBefore.contains("class ") && !textBefore.substring(textBefore.lastIndexOf("class ")).startsWith("// class");
    }

    /**
     * Determine what type of completion is needed
     */
    private CompletionContext determineCompletionContext(String currentLine, PsiFile file, int offset) {
        String trimmed = currentLine.trim();

        if (isInComment(file, offset)) {
            return CompletionContext.COMMENT;
        }

        if (trimmed.contains("import ") || trimmed.contains("from ")) {
            return CompletionContext.IMPORT_STATEMENT;
        }

        if (trimmed.contains("class ")) {
            return CompletionContext.CLASS_DEFINITION;
        }

        if (trimmed.contains("def ") || trimmed.contains("function ") ||
                (trimmed.contains("(") && (trimmed.contains("public ") || trimmed.contains("private ")))) {
            return CompletionContext.FUNCTION_DEFINITION;
        }

        if (trimmed.endsWith(":") || trimmed.endsWith("{") || trimmed.endsWith("(")) {
            return CompletionContext.BLOCK_START;
        }

        return CompletionContext.GENERAL_CODE;
    }

    /**
     * Get current line text
     */
    private String getCurrentLineText(@NotNull PsiFile file, int offset) {
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) return "";

        int lineNumber = document.getLineNumber(offset);
        int lineStart = document.getLineStartOffset(lineNumber);
        int lineEnd = document.getLineEndOffset(lineNumber);

        return document.getText(new TextRange(lineStart, lineEnd));
    }

    /**
     * Code context data class
     */
    public static class CodeContext {
        private final boolean inMethod;
        private final boolean inClass;
        private final boolean inComment;
        private final CompletionContext completionContext;
        private final String currentLine;

        public CodeContext(boolean inMethod, boolean inClass, boolean inComment,
                           CompletionContext completionContext, String currentLine) {
            this.inMethod = inMethod;
            this.inClass = inClass;
            this.inComment = inComment;
            this.completionContext = completionContext;
            this.currentLine = currentLine;
        }

        public boolean isInMethod() { return inMethod; }
        public boolean isInClass() { return inClass; }
        public boolean isInComment() { return inComment; }
        public CompletionContext getCompletionContext() { return completionContext; }
        public String getCurrentLine() { return currentLine; }
    }

    /**
     * Completion context types
     */
    public enum CompletionContext {
        COMMENT,
        FUNCTION_DEFINITION,
        CLASS_DEFINITION,
        IMPORT_STATEMENT,
        BLOCK_START,
        GENERAL_CODE
    }
}