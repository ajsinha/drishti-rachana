/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import com.ash.rachana.parse.YamlScanner.Kind;
import com.ash.rachana.parse.YamlScanner.Problem;
import com.ash.rachana.parse.YamlScanner.Token;
import com.ash.rachana.parse.YamlStream.Mark;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the scanner's tokens into events (document, mapping, sequence, scalar, alias), pulled one at a time. The
 * grammar, its error texts and the marks every event carries are those of the PyYAML family's parser, so positions and
 * refusals stay as the language defined them. Not thread-safe: one per read.
 */
final class YamlEvents {

    /** Event kinds. */
    enum Type { STREAM_START, STREAM_END, DOCUMENT_START, DOCUMENT_END, ALIAS, SCALAR, SEQUENCE_START, SEQUENCE_END, MAPPING_START, MAPPING_END }

    /**
     * One event. {@code tag} is the resolved tag or null; {@code plainImplicit} says the scalar was plain with no tag (or
     * tagged {@code !}), so its type is resolved from its text; {@code value} is a scalar's text or an alias's name.
     */
    record Event(Type type, Mark start, Mark end, String anchor, String tag, boolean plainImplicit, boolean quotedImplicit,
            boolean implicit, String value, char style) {

        static Event of(Type type, Mark start, Mark end) {
            return new Event(type, start, end, null, null, false, false, false, null, (char) 0);
        }

        /** The event as the original reader printed it in an error text. */
        @Override
        public String toString() {
            String name = switch (type) {
                case STREAM_START -> "StreamStartEvent";
                case STREAM_END -> "StreamEndEvent";
                case DOCUMENT_START -> "DocumentStartEvent";
                case DOCUMENT_END -> "DocumentEndEvent";
                case ALIAS -> "AliasEvent";
                case SCALAR -> "ScalarEvent";
                case SEQUENCE_START -> "SequenceStartEvent";
                case SEQUENCE_END -> "SequenceEndEvent";
                case MAPPING_START -> "MappingStartEvent";
                case MAPPING_END -> "MappingEndEvent";
            };
            String args = switch (type) {
                case ALIAS -> "anchor=" + value;
                case SCALAR -> "anchor=" + anchor + ", tag=" + tag + ", style=Scalar style: '" + (style == 0 ? "null" : String.valueOf(style))
                        + "',implicit=[" + plainImplicit + ", " + quotedImplicit + "], value=" + value;
                case SEQUENCE_START, MAPPING_START -> "anchor=" + anchor + ", tag=" + tag + ", implicit=" + implicit;
                default -> "";
            };
            return "<org.yaml.snakeyaml.events." + name + "(" + args + ")>";
        }
    }

    private interface Production {
        Event produce();
    }

    private static final Map<String, String> DEFAULT_TAGS = Map.of("!", "!", "!!", "tag:yaml.org,2002:");

    private final YamlScanner scanner;
    private final ArrayDeque<Production> states = new ArrayDeque<>();
    private final ArrayDeque<Mark> marks = new ArrayDeque<>();
    private Map<String, String> tagHandles = new HashMap<>(DEFAULT_TAGS);
    private boolean versionSeen;
    private Production state;
    private Event current;

    YamlEvents(YamlScanner scanner) {
        this.scanner = scanner;
        this.state = this::streamStart;
    }

    /** The next event, or null after the stream's end. */
    Event next() {
        if (current == null && state != null) {
            current = state.produce();
        }
        Event e = current;
        current = null;
        return e;
    }

    private Production pop() {
        return states.pop();
    }

    private Event streamStart() {
        Token t = scanner.next();
        state = this::implicitDocumentStart;
        return Event.of(Type.STREAM_START, t.start(), t.end());
    }

    private Event implicitDocumentStart() {
        if (!scanner.check(Kind.DIRECTIVE, Kind.DOCUMENT_START, Kind.STREAM_END)) {
            Mark start = scanner.peek().start();
            states.push(this::documentEnd);
            state = this::blockNode;
            return Event.of(Type.DOCUMENT_START, start, start);
        }
        return documentStart();
    }

    private Event documentStart() {
        while (scanner.check(Kind.DOCUMENT_END)) {
            scanner.next();
        }
        if (!scanner.check(Kind.STREAM_END)) {
            Mark start = scanner.peek().start();
            processDirectives();
            if (!scanner.check(Kind.STREAM_END)) {
                if (!scanner.check(Kind.DOCUMENT_START)) {
                    throw new Problem(null, null, "expected '<document start>', but found '" + scanner.peek().kind() + "'", scanner.peek().start());
                }
                Token t = scanner.next();
                states.push(this::documentEnd);
                state = this::documentContent;
                return Event.of(Type.DOCUMENT_START, start, t.end());
            }
        }
        Token t = scanner.next();
        if (!states.isEmpty()) {
            throw new YamlStream.Failure("Unexpected end of stream. States left: " + states);
        }
        state = null;
        return Event.of(Type.STREAM_END, t.start(), t.end());
    }

    private Event documentEnd() {
        Token t = scanner.peek();
        Mark start = t.start();
        Mark end = start;
        if (scanner.check(Kind.DOCUMENT_END)) {
            end = scanner.next().end();
        }
        state = this::documentStart;
        return Event.of(Type.DOCUMENT_END, start, end);
    }

    private Event documentContent() {
        if (scanner.check(Kind.DIRECTIVE, Kind.DOCUMENT_START, Kind.DOCUMENT_END, Kind.STREAM_END)) {
            Event e = emptyScalar(scanner.peek().start());
            state = pop();
            return e;
        }
        return blockNode();
    }

    private void processDirectives() {
        Map<String, String> handles = new HashMap<>(tagHandles);
        DEFAULT_TAGS.keySet().forEach(handles::remove);
        versionSeen = false;
        while (scanner.check(Kind.DIRECTIVE)) {
            Token t = scanner.next();
            List<String> value = t.args();
            if ("YAML".equals(t.value())) {
                if (versionSeen) {
                    throw new Problem(null, null, "found duplicate YAML directive", t.start());
                }
                if (!"1".equals(value.get(0))) {
                    throw new Problem(null, null, "found incompatible YAML document (version 1.* is required)", t.start());
                }
                versionSeen = true;
            } else if ("TAG".equals(t.value())) {
                if (handles.containsKey(value.get(0))) {
                    throw new Problem(null, null, "duplicate tag handle " + value.get(0), t.start());
                }
                handles.put(value.get(0), value.get(1));
            }
        }
        DEFAULT_TAGS.forEach(handles::putIfAbsent);
        tagHandles = handles;
    }

    private Event blockNode() {
        return node(true, false);
    }

    private Event flowNode() {
        return node(false, false);
    }

    private Event node(boolean block, boolean indentlessSequence) {
        if (scanner.check(Kind.ALIAS)) {
            Token t = scanner.next();
            state = pop();
            return new Event(Type.ALIAS, t.start(), t.end(), null, null, false, false, false, t.value(), (char) 0);
        }
        Mark start = null;
        Mark end = null;
        Mark tagMark = null;
        String anchor = null;
        Token tagToken = null;
        if (scanner.check(Kind.ANCHOR)) {
            Token t = scanner.next();
            start = t.start();
            end = t.end();
            anchor = t.value();
            if (scanner.check(Kind.TAG)) {
                tagToken = scanner.next();
                tagMark = tagToken.start();
                end = tagToken.end();
            }
        } else if (scanner.check(Kind.TAG)) {
            tagToken = scanner.next();
            start = tagToken.start();
            tagMark = start;
            end = tagToken.end();
            if (scanner.check(Kind.ANCHOR)) {
                Token t = scanner.next();
                end = t.end();
                anchor = t.value();
            }
        }
        String tag = null;
        if (tagToken != null) {
            String handle = tagToken.handle();
            if (handle != null) {
                if (!tagHandles.containsKey(handle)) {
                    throw new Problem("while parsing a node", start, "found undefined tag handle " + handle, tagMark);
                }
                tag = tagHandles.get(handle) + tagToken.suffix();
            } else {
                tag = tagToken.suffix();
            }
        }
        if (start == null) {
            start = scanner.peek().start();
            end = start;
        }
        boolean implicit = tag == null || tag.equals("!");
        if (indentlessSequence && scanner.check(Kind.BLOCK_ENTRY)) {
            end = scanner.peek().end();
            state = this::indentlessSequenceEntryKey;
            return new Event(Type.SEQUENCE_START, start, end, anchor, tag, false, false, implicit, null, (char) 0);
        }
        if (scanner.check(Kind.SCALAR)) {
            Token t = scanner.next();
            end = t.end();
            boolean plainImplicit = t.plain() && tag == null || "!".equals(tag);
            boolean quotedImplicit = !plainImplicit && tag == null;
            state = pop();
            return new Event(Type.SCALAR, start, end, anchor, tag, plainImplicit, quotedImplicit, false, t.value(), t.style());
        }
        if (scanner.check(Kind.FLOW_SEQUENCE_START)) {
            end = scanner.peek().end();
            state = this::flowSequenceFirstEntry;
            return new Event(Type.SEQUENCE_START, start, end, anchor, tag, false, false, implicit, null, (char) 0);
        }
        if (scanner.check(Kind.FLOW_MAPPING_START)) {
            end = scanner.peek().end();
            state = this::flowMappingFirstKey;
            return new Event(Type.MAPPING_START, start, end, anchor, tag, false, false, implicit, null, (char) 0);
        }
        if (block && scanner.check(Kind.BLOCK_SEQUENCE_START)) {
            end = scanner.peek().start();
            state = this::blockSequenceFirstEntry;
            return new Event(Type.SEQUENCE_START, start, end, anchor, tag, false, false, implicit, null, (char) 0);
        }
        if (block && scanner.check(Kind.BLOCK_MAPPING_START)) {
            end = scanner.peek().start();
            state = this::blockMappingFirstKey;
            return new Event(Type.MAPPING_START, start, end, anchor, tag, false, false, implicit, null, (char) 0);
        }
        if (anchor != null || tag != null) {
            state = pop();
            return new Event(Type.SCALAR, start, end, anchor, tag, implicit, false, false, "", (char) 0);
        }
        Token t = scanner.peek();
        throw new Problem("while parsing a " + (block ? "block" : "flow") + " node", start,
                "expected the node content, but found '" + t.kind() + "'", t.start());
    }

    private Event blockSequenceFirstEntry() {
        marks.push(scanner.next().start());
        return blockSequenceEntryKey();
    }

    private Event blockSequenceEntryKey() {
        if (scanner.check(Kind.BLOCK_ENTRY)) {
            Token t = scanner.next();
            if (!scanner.check(Kind.BLOCK_ENTRY, Kind.BLOCK_END)) {
                states.push(this::blockSequenceEntryKey);
                return blockNode();
            }
            state = this::blockSequenceEntryKey;
            return emptyScalar(t.end());
        }
        if (!scanner.check(Kind.BLOCK_END)) {
            Token t = scanner.peek();
            throw new Problem("while parsing a block collection", marks.pop(), "expected <block end>, but found '" + t.kind() + "'", t.start());
        }
        Token t = scanner.next();
        state = pop();
        marks.pop();
        return Event.of(Type.SEQUENCE_END, t.start(), t.end());
    }

    private Event indentlessSequenceEntryKey() {
        if (scanner.check(Kind.BLOCK_ENTRY)) {
            Token t = scanner.next();
            if (!scanner.check(Kind.BLOCK_ENTRY, Kind.KEY, Kind.VALUE, Kind.BLOCK_END)) {
                states.push(this::indentlessSequenceEntryKey);
                return blockNode();
            }
            state = this::indentlessSequenceEntryKey;
            return emptyScalar(t.end());
        }
        Token t = scanner.peek();
        state = pop();
        return Event.of(Type.SEQUENCE_END, t.start(), t.end());
    }

    private Event blockMappingFirstKey() {
        marks.push(scanner.next().start());
        return blockMappingKey();
    }

    private Event blockMappingKey() {
        if (scanner.check(Kind.KEY)) {
            Token t = scanner.next();
            if (!scanner.check(Kind.KEY, Kind.VALUE, Kind.BLOCK_END)) {
                states.push(this::blockMappingValue);
                return node(true, true);
            }
            state = this::blockMappingValue;
            return emptyScalar(t.end());
        }
        if (!scanner.check(Kind.BLOCK_END)) {
            Token t = scanner.peek();
            throw new Problem("while parsing a block mapping", marks.pop(), "expected <block end>, but found '" + t.kind() + "'", t.start());
        }
        Token t = scanner.next();
        state = pop();
        marks.pop();
        return Event.of(Type.MAPPING_END, t.start(), t.end());
    }

    private Event blockMappingValue() {
        if (scanner.check(Kind.VALUE)) {
            Token t = scanner.next();
            if (!scanner.check(Kind.KEY, Kind.VALUE, Kind.BLOCK_END)) {
                states.push(this::blockMappingKey);
                return node(true, true);
            }
            state = this::blockMappingKey;
            return emptyScalar(t.end());
        } else if (scanner.check(Kind.SCALAR)) {
            states.push(this::blockMappingKey);
            return node(true, true);
        }
        state = this::blockMappingKey;
        return emptyScalar(scanner.peek().start());
    }

    private Event flowSequenceFirstEntry() {
        marks.push(scanner.next().start());
        return flowSequenceEntry(true);
    }

    private Event flowSequenceEntry(boolean first) {
        if (!scanner.check(Kind.FLOW_SEQUENCE_END)) {
            if (!first) {
                if (scanner.check(Kind.FLOW_ENTRY)) {
                    scanner.next();
                } else {
                    Token t = scanner.peek();
                    throw new Problem("while parsing a flow sequence", marks.pop(), "expected ',' or ']', but got " + t.kind(), t.start());
                }
            }
            if (scanner.check(Kind.KEY)) {
                Token t = scanner.peek();
                state = this::flowSequenceEntryMappingKey;
                return new Event(Type.MAPPING_START, t.start(), t.end(), null, null, false, false, true, null, (char) 0);
            } else if (!scanner.check(Kind.FLOW_SEQUENCE_END)) {
                states.push(() -> flowSequenceEntry(false));
                return flowNode();
            }
        }
        Token t = scanner.next();
        scanner.check(); // the original looks one token ahead here (for a comment): an error after ']' surfaces before this event
        state = pop();
        marks.pop();
        return Event.of(Type.SEQUENCE_END, t.start(), t.end());
    }

    private Event flowSequenceEntryMappingKey() {
        Token t = scanner.next();
        if (!scanner.check(Kind.VALUE, Kind.FLOW_ENTRY, Kind.FLOW_SEQUENCE_END)) {
            states.push(this::flowSequenceEntryMappingValue);
            return flowNode();
        }
        state = this::flowSequenceEntryMappingValue;
        return emptyScalar(t.end());
    }

    private Event flowSequenceEntryMappingValue() {
        if (scanner.check(Kind.VALUE)) {
            Token t = scanner.next();
            if (!scanner.check(Kind.FLOW_ENTRY, Kind.FLOW_SEQUENCE_END)) {
                states.push(this::flowSequenceEntryMappingEnd);
                return flowNode();
            }
            state = this::flowSequenceEntryMappingEnd;
            return emptyScalar(t.end());
        }
        state = this::flowSequenceEntryMappingEnd;
        return emptyScalar(scanner.peek().start());
    }

    private Event flowSequenceEntryMappingEnd() {
        state = () -> flowSequenceEntry(false);
        Token t = scanner.peek();
        return Event.of(Type.MAPPING_END, t.start(), t.end());
    }

    private Event flowMappingFirstKey() {
        marks.push(scanner.next().start());
        return flowMappingKey(true);
    }

    private Event flowMappingKey(boolean first) {
        if (!scanner.check(Kind.FLOW_MAPPING_END)) {
            if (!first) {
                if (scanner.check(Kind.FLOW_ENTRY)) {
                    scanner.next();
                } else {
                    Token t = scanner.peek();
                    throw new Problem("while parsing a flow mapping", marks.pop(), "expected ',' or '}', but got " + t.kind(), t.start());
                }
            }
            if (scanner.check(Kind.KEY)) {
                Token t = scanner.next();
                if (!scanner.check(Kind.VALUE, Kind.FLOW_ENTRY, Kind.FLOW_MAPPING_END)) {
                    states.push(this::flowMappingValue);
                    return flowNode();
                }
                state = this::flowMappingValue;
                return emptyScalar(t.end());
            } else if (!scanner.check(Kind.FLOW_MAPPING_END)) {
                states.push(this::flowMappingEmptyValue);
                return flowNode();
            }
        }
        Token t = scanner.next();
        scanner.check(); // as after ']'
        marks.pop();
        state = pop();
        return Event.of(Type.MAPPING_END, t.start(), t.end());
    }

    private Event flowMappingValue() {
        if (scanner.check(Kind.VALUE)) {
            Token t = scanner.next();
            if (!scanner.check(Kind.FLOW_ENTRY, Kind.FLOW_MAPPING_END)) {
                states.push(() -> flowMappingKey(false));
                return flowNode();
            }
            state = () -> flowMappingKey(false);
            return emptyScalar(t.end());
        }
        state = () -> flowMappingKey(false);
        return emptyScalar(scanner.peek().start());
    }

    private Event flowMappingEmptyValue() {
        state = () -> flowMappingKey(false);
        return emptyScalar(scanner.peek().start());
    }

    private static Event emptyScalar(Mark mark) {
        return new Event(Type.SCALAR, mark, mark, null, null, true, false, false, "", (char) 0);
    }
}
