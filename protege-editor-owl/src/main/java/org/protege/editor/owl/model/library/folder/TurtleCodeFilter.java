package org.protege.editor.owl.model.library.folder;

import java.util.ArrayList;
import java.util.List;

/**
 * Reduces the head of a Turtle document to its "code" so that regex-based
 * detection of the ontology declaration cannot be fooled by look-alike text
 * (review finding F5). Per line, in document order:
 * <ul>
 * <li>long string literals ({@code """..."""} and {@code '''...'''}), including
 *     ones spanning several lines, are replaced by an empty literal {@code ""};</li>
 * <li>short string literals ({@code "..."} and {@code '...'}, with backslash
 *     escapes) are replaced by {@code ""};</li>
 * <li>IRIs in angle brackets are kept verbatim (a {@code #} inside one is a
 *     fragment, not a comment);</li>
 * <li>a {@code #} outside strings and IRIs starts a comment that runs to the end
 *     of the line and is dropped.</li>
 * </ul>
 * Everything else is copied unchanged, so line numbers and statement
 * terminators ({@code ;} and {@code .}) survive for the caller's patterns.
 */
final class TurtleCodeFilter {

    private TurtleCodeFilter() {
    }

    static List<String> codeLines(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        String openLongString = null; // """ or ''' while inside a long string
        for (String line : lines) {
            StringBuilder code = new StringBuilder(line.length());
            int i = 0;
            while (i < line.length()) {
                if (openLongString != null) {
                    int close = line.indexOf(openLongString, i);
                    if (close < 0) {
                        i = line.length();       // whole rest of line is literal text
                    }
                    else {
                        i = close + 3;
                        openLongString = null;
                        code.append("\"\"");
                    }
                    continue;
                }
                char c = line.charAt(i);
                if (line.startsWith("\"\"\"", i) || line.startsWith("'''", i)) {
                    openLongString = line.substring(i, i + 3);
                    i += 3;
                }
                else if (c == '"' || c == '\'') {
                    i = skipShortString(line, i);
                    code.append("\"\"");
                }
                else if (c == '<') {
                    int close = line.indexOf('>', i);
                    int end = close < 0 ? line.length() : close + 1;
                    code.append(line, i, end);
                    i = end;
                }
                else if (c == '#') {
                    break;                       // comment to end of line
                }
                else {
                    code.append(c);
                    i++;
                }
            }
            out.add(code.toString());
        }
        return out;
    }

    /** Returns the index just past the closing quote (or the line end if unterminated). */
    private static int skipShortString(String line, int openIndex) {
        char quote = line.charAt(openIndex);
        int i = openIndex + 1;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\') {
                i += 2;
            }
            else if (c == quote) {
                return i + 1;
            }
            else {
                i++;
            }
        }
        return line.length();
    }
}
