package org.protege.editor.owl.model.library.folder;

import java.util.ArrayList;
import java.util.List;

/**
 * Strips the start of a Turtle document down to its structure, so the ontology
 * declaration can be found with simple patterns that are not fooled by text
 * inside strings or comments. For each line, in order:
 * <ul>
 * <li>string literals become {@code ""}, whether short ({@code "..."},
 *     {@code '...'}) or long ({@code """..."""}, {@code '''...'''}, possibly
 *     spanning several lines);</li>
 * <li>IRIs in angle brackets are kept as they are (a {@code #} inside one is
 *     not a comment);</li>
 * <li>a {@code #} outside strings and IRIs starts a comment, which is dropped
 *     to the end of the line.</li>
 * </ul>
 * Everything else is kept, so line numbers and the {@code ;} and {@code .} that
 * end statements stay where they were.
 */
final class TurtleFilter {

    private TurtleFilter() {
    }

    static List<String> codeLines(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        String openLongString = null; // """ or ''' while inside a long string
        for (String line : lines) {
            StringBuilder code = new StringBuilder(line.length());
            int i = 0;
            while (i < line.length()) {
                if (openLongString != null) {
                    int end = endOfLongString(line, i, openLongString);
                    if (end < 0) {
                        i = line.length();       // whole rest of line is literal text
                    }
                    else {
                        i = end;
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

    /**
     * Returns the index just past the delimiter that closes a long string, or -1 when
     * the string is still open at the end of the line. Quotes that are part of a
     * backslash escape do not close the string: a literal ending in {@code \"} puts
     * four quotes in a row before the real delimiter.
     */
    private static int endOfLongString(String line, int from, String delimiter) {
        int i = from;
        while (i < line.length()) {
            if (line.charAt(i) == '\\') {
                i += 2;
            }
            else if (line.startsWith(delimiter, i)) {
                return i + delimiter.length();
            }
            else {
                i++;
            }
        }
        return -1;
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
