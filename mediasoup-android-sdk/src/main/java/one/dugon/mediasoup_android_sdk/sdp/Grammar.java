package one.dugon.mediasoup_android_sdk.sdp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Grammar {
    String name;
    String regex;
    String push;
    String[] names;
    //    String format;
    public Function<JsonElement, String> format;

    public static Map<Character, Grammar[]> data = Map.of(
            'v', new Grammar[]{
                    new Grammar("version", "^(\\d*)$")
            },
            'o', new Grammar[]{
                    new Grammar("origin", "^(\\S*) (\\d*) (\\d*) (\\S*) IP(\\d) (\\S*)",
                            new String[]{
                                    "username", "sessionId", "sessionVersion", "netType", "ipVer", "address"
                            }, x -> "%s %s %d %s IP%d %s")
            },
            's', new Grammar[]{
                    new Grammar("name")
            },
            't', new Grammar[]{
                    new Grammar("timing", "^(\\d*) (\\d*)",
                            new String[]{"start", "stop" },
                            x -> "%d %d")
            },

            'c', new Grammar[]{
                    new Grammar(
                            "connection",
                            "^IN IP(\\d) (\\S*)",
                            new String[]{"version", "ip" },
                            x -> "IN IP%d %s"
                    )
            },
            'b', new Grammar[]{
                    new Grammar("",
                            "^(TIAS|AS|CT|RR|RS):(\\d*)",
                            "bandwidth",
                            new String[]{"type", "limit" },
                            x -> "%s:%s")
            },
            'm', new Grammar[]{
                    new Grammar(
                            "",
                            "^(\\w*) (\\d*) ([\\w/]*)(?: (.*))?",
                            new String[]{"type", "port", "protocol", "payloads" },
                            x -> "%s %d %s %s"
                    )
            },
            'a', new Grammar[]{
                    new Grammar(
                            "",
                            "^candidate:(\\S*) (\\d*) (\\S*) (\\d*) (\\S*) (\\d*) typ (\\S*)(?: raddr (\\S*) rport (\\d*))?(?: tcptype (\\S*))?(?: generation (\\d*))?(?: network-id (\\d*))?(?: network-cost (\\d*))?",
                            "candidates",
                            new String[]{"foundation", "component", "transport", "priority", "ip", "port", "type", "raddr", "rport", "tcptype", "generation", "network-id", "network-cost" },
                            x -> {
                                String str = "candidate:%s %d %s %d %s %d typ %s";

                                str += jsonHasNotNull(x,"raddr") ? " raddr %s rport %d" : "%v%v";

                                // NB: candidate has three optional chunks, so %void middles one if it's missing
                                str += jsonHasNotNull(x, "tcptype") ? " tcptype %s" : "%v";

                                if (jsonHasNotNull(x, "generation")) {
                                    str += " generation %d";
                                }

                                str += jsonHasNotNull(x, "network-id") ? " network-id %d" : "%v";
                                str += jsonHasNotNull(x, "network-cost") ? " network-cost %d" : "%v";
                                return  str;
                            }
                    ),
                    new Grammar(
                            "",
                            "^rtpmap:(\\d*) ([\\w\\-.]*)(?:\\s*\\/(\\d*)(?:\\s*\\/(\\S*))?)?",
                            "rtp",
                            new String[]{"payload", "codec", "rate", "encoding" },
                            x -> jsonHasNotNull(x, "encoding")
                                    ? "rtpmap:%d %s/%s/%s"
                                    : jsonHasNotNull(x, "rate")
                                    ? "rtpmap:%d %s/%s"
                                    : "rtpmap:%d %s"
                    ),
                    new Grammar(
                            "",
                            "^fmtp:(\\d*) ([\\S| ]*)",
                            "fmtp",
                            new String[]{"payload", "config",},
                            x -> "fmtp:%d %s"
                    ),
                    new Grammar(
                            "",
                            "^rtcp:(\\d*)(?: (\\S*) IP(\\d) (\\S*))?",
                            "rtcp",
                            new String[]{"port", "netType", "ipVer", "address" },
                            x -> jsonHasNotEmpty(x,"address")
                                    ? "rtcp:%d %s IP%d %s"
                                    : "rtcp:%d"
                    ),
                    new Grammar(
                            "",
                            "^rtcp-fb:(\\*|\\d*) ([\\w-_]*)(?: ([\\w-_]*))?",
                            "rtcpFb",
                            new String[]{"payload", "type", "subtype" },
                            x -> jsonHasNotNull(x, "subtype")
                                    ? "rtcp-fb:%s %s %s"
                                    : "rtcp-fb:%s %s"
                    ),
                    new Grammar(
                            "",
                            "^extmap:(\\d+)(?:\\/(\\w+))?(?: (urn:ietf:params:rtp-hdrext:encrypt))? (\\S*)(?: (\\S*))?",
                            "ext",
                            new String[]{"value", "direction", "encrypt-uri", "uri", "config" },
                            x -> (
                                    "extmap:%d" +
                                            (jsonHasNotNull(x, "direction") ? "/%s" : "%v") +
                                            (jsonHasNotNull(x, "encrypt-uri") ? " %s" : "%v") +
                                            " %s" +
                                            (jsonHasNotNull(x, "config") ? " %s" : "")
                            )
                    ),
                    new Grammar(
                            "extmapAllowMixed",
                            "^(extmap-allow-mixed)"
                    ),
                    new Grammar(
                            "setup",
                            "^setup:(\\w*)",
                            x-> "setup:%s"
                    ),
                    new Grammar(
                            "mid",
                            "^mid:([^\\s]*)",
                            x-> "mid:%s"
                    ),
                    new Grammar(
                            "direction",
                            "^(sendrecv|recvonly|sendonly|inactive)"
                    ),
                    new Grammar(
                            "icelite",
                            "^(ice-lite)"
                    ),
                    new Grammar(
                            "iceUfrag",
                            "^ice-ufrag:(\\S*)",
                            x-> "ice-ufrag:%s"
                    ),
                    new Grammar(
                            "icePwd",
                            "^ice-pwd:(\\S*)",
                            x-> "ice-pwd:%s"
                    ),
                    new Grammar(
                            "iceOptions",
                            "^ice-options:(\\S*)",
                            x-> "ice-options:%s"
                    ),
                    new Grammar(
                            "fingerprint",
                            "^fingerprint:(\\S*) (\\S*)",
                            new String[]{"type", "hash" },
                            x-> "fingerprint:%s %s"
                    ),
                    new Grammar(
                            "msidSemantic",
                            "^msid-semantic:\\s?(\\w*) (\\S*)",
                            new String[]{"semantic", "token" },
                            x-> jsonHasNotEmpty(x,"semantic") ? "msid-semantic: %s %s":"msid-semantic: %v%s"
                    ),
                    new Grammar(
                            "groups",
                            "^group:(\\w*) (.*)",
                            new String[]{"type", "mids" },
                            x-> "group:%s %s"
                    ),
                    new Grammar(
                            "rtcpMux",
                            "^(rtcp-mux)"
                    ),
                    new Grammar(
                            "rtcpRsize",
                            "^(rtcp-rsize)"
                    ),
                    new Grammar(
                            "",
                            "^ssrc-group:([\\x21\\x23\\x24\\x25\\x26\\x27\\x2A\\x2B\\x2D\\x2E\\w]*) (.*)",
                            "ssrcGroups",
                            new String[]{"semantics", "ssrcs" },
                            x -> "ssrc-group:%s %s"
                    ),
                    new Grammar(
                            "",
                            "^ssrc:(\\d*) ([\\w_-]*)(?::(.*))?",
                            "ssrcs",
                            new String[]{"id", "attribute", "value", },
                            x -> {
                                String str = "ssrc:%d";
                                if(jsonHasNotNull(x,"attribute")){
                                    str += " %s";
                                    if(jsonHasNotNull(x,"value")){
                                        str += ":%s";
                                    }
                                }
                                return str;
                            }
                    ),

}
    );

    public Grammar(String name) {
        this(name, "(.*)");
    }

    public Grammar(String name, String regex) {
        this(name, regex, new String[]{});
    }

    public Grammar(String name, String regex, String[] names) {
        this(name, regex, "", names, x -> "%s");
    }

    public Grammar(String name, String regex, Function<JsonElement, String> format) {
        this(name, regex, "", new String[]{}, format);
    }

    public Grammar(String name, String regex, String[] names, Function<JsonElement, String> format) {
        this(name, regex, "", names, format);
    }


    public Grammar(String name, String regex, String push, String[] names, Function<JsonElement, String> format) {
        this.name = name;
        this.regex = regex;
        this.push = push;
        this.names = names;
        this.format = format;
    }

    public boolean check(String content) {
        Matcher matcher = match(content);
        return matcher.find();
    }

    public Matcher match(String content) {
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(content);
    }

    public boolean needsBlank() {
        return !name.isEmpty() && names.length > 0;
    }

    public static boolean jsonHasNotNull(JsonElement e, String field) {
        JsonObject object = e.getAsJsonObject();
        return object.has(field) && !object.get(field).isJsonNull();
    }


    public static boolean jsonHasNotEmpty(JsonElement e, String field) {
        JsonObject object = e.getAsJsonObject();
        return object.has(field) && object.get(field).isJsonPrimitive() && !object.get(field).getAsString().isEmpty() ;
    }
}
