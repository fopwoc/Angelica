package com.gtnewhorizons.angelica.sdlgpu.geometry;

import java.util.*;
import org.antlr.v4.runtime.*;
import org.taumc.glsl.grammar.GLSLLexer;

/** Token boundaries for preprocessed geometry-pipeline stages; no textual substitution inside comments. */
final class GeometrySyntax {
    record Declaration(int start, int end, List<Token> tokens) {}

    record Parsed(CommonTokenStream stream, List<Token> tokens, List<Declaration> declarations) {}

    private GeometrySyntax() {}

    static Parsed parse(String preprocessed) {
        if (preprocessed.contains("_geometry_"))
            throw new IllegalArgumentException("Reserved geometry transport identifier");
        var source = new StringBuilder();
        for (String line : preprocessed.split("\\R")) {
            if (line.stripLeading().startsWith("#")) {
                if (line.matches("\\s*#\\s*(?:version|line)\\b.*")) continue;
                throw new UnsupportedOperationException(
                        "Geometry compute requires preprocessed source without directives");
            }
            source.append(line).append('\n');
        }
        var lexer = new GLSLLexer(CharStreams.fromString(source.toString()));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(
                    Recognizer<?, ?> recognizer,
                    Object symbol,
                    int line,
                    int column,
                    String message,
                    RecognitionException failure) {
                throw new IllegalArgumentException("Geometry token at " + line + ":" + column + ": " + message);
            }
        });
        var stream = new CommonTokenStream(lexer);
        stream.fill();
        var tokens = stream.getTokens().stream()
                .filter(t -> t.getChannel() == Token.DEFAULT_CHANNEL && t.getType() != Token.EOF)
                .toList();
        return new Parsed(stream, tokens, declarations(tokens));
    }

    static int storage(List<String> words) {
        int depth = 0;
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            if (word.equals("(")) depth++;
            else if (word.equals(")")) depth--;
            else if (depth == 0 && (word.equals("in") || word.equals("out"))) return i;
        }
        return -1;
    }

    private static List<Declaration> declarations(List<Token> tokens) {
        var result = new ArrayList<Declaration>();
        int start = 0, braces = 0;
        boolean function = false;
        for (int i = 0; i < tokens.size(); i++) {
            String text = tokens.get(i).getText();
            if (text.equals("{")) {
                if (braces == 0) function = i > 0 && tokens.get(i - 1).getText().equals(")");
                braces++;
            } else if (text.equals("}")) {
                if (--braces < 0) throw new IllegalArgumentException("Unbalanced geometry braces");
                if (braces == 0 && function) start = i + 1;
            } else if (text.equals(";") && braces == 0) {
                result.add(new Declaration(
                        tokens.get(start).getTokenIndex(),
                        tokens.get(i).getTokenIndex(),
                        tokens.subList(start, i + 1)));
                start = i + 1;
            }
        }
        if (braces != 0) throw new IllegalArgumentException("Unbalanced geometry braces");
        return result;
    }
}
