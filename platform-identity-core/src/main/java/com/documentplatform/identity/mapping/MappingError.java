package com.documentplatform.identity.mapping;

/**
 * One reason a role mapping file is refused. {@code line} is the line in the file (1-based), or 0 for the file as a
 * whole. The message is written for the installer who edits the file.
 */
public record MappingError(int line, Code code, String message) {

    public enum Code {
        NOT_UTF8,
        BAD_QUOTES,
        VALUE_SPANS_LINES,
        UNSUPPORTED_FORMAT,
        BAD_HEADER,
        WRONG_COLUMN_COUNT,
        BLANK_VALUE,
        INVISIBLE_CHARACTER,
        NOT_AN_ENTITY_ID,
        UNKNOWN_ENTITY,
        ENTITY_NOT_ACTIVE,
        ENTITY_NAME_MISMATCH,
        CONFLICTING_ENTITY_NAMES,
        NO_ACTIVE_LOGIN_SYSTEM,
        UNKNOWN_ROLE,
        ROLE_NOT_WRITTEN_CANONICALLY,
        PLATFORM_ROLE,
        GROUP_TOO_LONG,
        DUPLICATE_ROW,
        NO_ROWS
    }

    /** The message as an installer sees it: {@code line 6: unknown role "DOCUMENT_PROCESOR"}. */
    public String display() {
        return line > 0 ? "line " + line + ": " + message : message;
    }
}
