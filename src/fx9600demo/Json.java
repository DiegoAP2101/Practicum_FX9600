package fx9600demo;

/** Mini utilidades para escribir JSON sin dependencias externas. */
final class Json {

    private Json() {
    }

    static StringBuilder field(StringBuilder sb, String name, String value) {
        sb.append('"').append(name).append("\":");
        appendString(sb, value);
        return sb;
    }

    static StringBuilder field(StringBuilder sb, String name, long value) {
        sb.append('"').append(name).append("\":").append(value);
        return sb;
    }

    static StringBuilder field(StringBuilder sb, String name, double value) {
        sb.append('"').append(name).append("\":").append(value);
        return sb;
    }

    static StringBuilder field(StringBuilder sb, String name, boolean value) {
        sb.append('"').append(name).append("\":").append(value);
        return sb;
    }

    static void appendString(StringBuilder sb, String s) {
        sb.append('"');
        if (s != null) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default:
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                }
            }
        }
        sb.append('"');
    }
}
