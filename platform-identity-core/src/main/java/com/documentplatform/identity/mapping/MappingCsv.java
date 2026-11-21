package com.documentplatform.identity.mapping;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a role mapping file as CSV (RFC 4180), because installers open and save it in spreadsheet programs:
 * quoted fields, commas inside quotes, doubled quotes, CRLF or LF line ends and a UTF-8 byte-order mark are accepted.
 * Lines starting with {@code #} outside quotes are comments; empty lines are skipped. A value that spans more than
 * one line is refused: a line break inside a group name is a fault, not data. Nothing is trimmed here.
 */
final class MappingCsv {

    /** One CSV record, with the physical line it starts on (1-based). */
    record Record(int line, List<String> fields) {
    }

    /** One comment line, without the leading {@code #}. */
    record Comment(int line, String text) {
    }

    record Parsed(List<Record> records, List<Comment> comments, List<MappingError> errors) {
    }

    private MappingCsv() {
    }

    static Parsed parse(byte[] content) {
        List<Record> records = new ArrayList<>();
        List<Comment> comments = new ArrayList<>();
        List<MappingError> errors = new ArrayList<>();
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException notUtf8) {
            errors.add(new MappingError(0, MappingError.Code.NOT_UTF8, "the file is not UTF-8 text; save it as \"CSV UTF-8\""));
            return new Parsed(records, comments, errors);
        }
        if (text.startsWith("\uFEFF")) text = text.substring(1);

        int line = 1;
        int i = 0;
        int n = text.length();
        while (i < n) {
            // Start of a physical line, outside any record.
            char first = text.charAt(i);
            if (first == '\r' || first == '\n') {
                i = skipLineEnd(text, i);
                line++;
                continue;
            }
            if (first == '#') {
                int end = i;
                while (end < n && text.charAt(end) != '\r' && text.charAt(end) != '\n') end++;
                comments.add(new Comment(line, text.substring(i + 1, end)));
                i = end < n ? skipLineEnd(text, end) : end;
                line++;
                continue;
            }
            int recordLine = line;
            List<String> fields = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean spansLines = false;
            boolean badQuote = false;
            boolean endOfRecord = false;
            while (!endOfRecord) {
                if (i < n && text.charAt(i) == '"') {
                    i++;
                    boolean closed = false;
                    while (i < n) {
                        char c = text.charAt(i);
                        if (c == '"') {
                            if (i + 1 < n && text.charAt(i + 1) == '"') {
                                field.append('"');
                                i += 2;
                            } else {
                                i++;
                                closed = true;
                                break;
                            }
                        } else {
                            if (c == '\r' || c == '\n') {
                                spansLines = true;
                                if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') {
                                    field.append(c);
                                    i++;
                                    c = '\n';
                                }
                                line++;
                            }
                            field.append(c);
                            i++;
                        }
                    }
                    if (!closed) badQuote = true;
                    if (i < n && text.charAt(i) != ',' && text.charAt(i) != '\r' && text.charAt(i) != '\n') {
                        badQuote = true;
                        while (i < n && text.charAt(i) != ',' && text.charAt(i) != '\r' && text.charAt(i) != '\n') field.append(text.charAt(i++));
                    }
                } else {
                    while (i < n && text.charAt(i) != ',' && text.charAt(i) != '\r' && text.charAt(i) != '\n') {
                        if (text.charAt(i) == '"') badQuote = true;
                        field.append(text.charAt(i++));
                    }
                }
                fields.add(field.toString());
                field.setLength(0);
                if (i < n && text.charAt(i) == ',') {
                    i++;
                } else {
                    endOfRecord = true;
                    if (i < n) {
                        i = skipLineEnd(text, i);
                    }
                    line++;
                }
            }
            if (badQuote) {
                errors.add(new MappingError(recordLine, MappingError.Code.BAD_QUOTES,
                        "quotes are not closed or a quoted value is followed by more text"));
            } else if (spansLines) {
                errors.add(new MappingError(recordLine, MappingError.Code.VALUE_SPANS_LINES,
                        "a value contains a line break; each value must be on one line"));
            } else {
                records.add(new Record(recordLine, List.copyOf(fields)));
            }
        }
        return new Parsed(records, comments, errors);
    }

    private static int skipLineEnd(String text, int i) {
        if (text.charAt(i) == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') return i + 2;
        return i + 1;
    }
}
