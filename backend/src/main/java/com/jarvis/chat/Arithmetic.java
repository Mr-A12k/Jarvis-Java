package com.jarvis.chat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;
import com.jarvis.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Explicit calculator syntax only. No scripting, SQL or model execution. */
@Component
public class Arithmetic {
    public Optional<String> answer(String input) {
        if (!input.stripLeading().toLowerCase(java.util.Locale.ROOT).startsWith("calculate:")) return Optional.empty();
        String expression = input.strip().substring(10).trim();
        if (expression.isEmpty() || expression.length() > 256 || !expression.matches("[0-9+*/().\\s-]+")) throw invalid();
        try {
            Parser parser = new Parser(expression);
            BigDecimal result = parser.expression(0);
            parser.space();
            if (parser.pos != expression.length()) throw invalid();
            return Optional.of("Calculator result: " + result.stripTrailingZeros().toPlainString());
        } catch (ArithmeticException | NumberFormatException e) { throw invalid(); }
    }
    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CALCULATION", "Use calculate: followed by numbers, parentheses and + - * /. Maximum 256 characters; division by zero is not allowed.");
    }
    private static class Parser {
        final String text; int pos;
        Parser(String text) { this.text = text; }
        void space() { while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++; }
        boolean eat(char c) { space(); if (pos < text.length() && text.charAt(pos)==c) { pos++; return true; } return false; }
        BigDecimal expression(int depth) {
            var result = term(depth);
            while (true) { if (eat('+')) result=result.add(term(depth),MathContext.DECIMAL128); else if (eat('-')) result=result.subtract(term(depth),MathContext.DECIMAL128); else return result; }
        }
        BigDecimal term(int depth) {
            var result = atom(depth);
            while (true) { if (eat('*')) result=result.multiply(atom(depth),MathContext.DECIMAL128); else if (eat('/')) result=result.divide(atom(depth),MathContext.DECIMAL128); else return result; }
        }
        BigDecimal atom(int depth) {
            if (depth > 24) throw invalid();
            if (eat('+')) return atom(depth+1);
            if (eat('-')) return atom(depth+1).negate();
            if (eat('(')) { var value=expression(depth+1); if (!eat(')')) throw invalid(); return value; }
            space(); int start=pos;
            while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos)=='.')) pos++;
            if (start==pos) throw invalid();
            return new BigDecimal(text.substring(start,pos),MathContext.DECIMAL128);
        }
    }
}
