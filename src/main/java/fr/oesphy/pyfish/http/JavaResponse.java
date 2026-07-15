package fr.oesphy.pyfish.http;

import java.util.List;
import java.util.Map;

public class JavaResponse {
    public final int status_code;
    public final String text;
    public final Map<String, List<String>> headers;

    public JavaResponse(int status_code, String text, Map<String, List<String>> headers) {
        this.status_code = status_code;
        this.text = text;
        this.headers = headers;
    }

    public String getBody() {
        return text;
    }
}
