package one.dugon.mediasoup_android_sdk.sdp;


import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Writer {
    private static final String TAG = "Writer";

    static Character[] defaultOuterOrder = new Character[]{
            'v', 'o', 's', 'c',
            'b', 't', 'a'
    };
    static Character[] defaultInnerOrder = new Character[]{'c', 'b', 'a'};


    public static String  write(JsonObject session) {
        ArrayList<String> sdp = new ArrayList<String>();

        for (Character c : defaultOuterOrder) {
            Log.d(TAG,c+"");
            Grammar[] gs = Grammar.data.get(c);
            assert gs != null;
            for (Grammar g : gs) {
                if (session.has(g.name)) {
                    sdp.add(makeLine(c, g, session));
                }
            }
        }

        JsonArray media = session.getAsJsonArray("media");

        for (JsonElement m : media) {
            sdp.add(makeLine('m', Grammar.data.get('m')[0], m.getAsJsonObject()));

            for (Character c : defaultInnerOrder) {
                Log.d(TAG,"ccc:"+c);
                for (Grammar g : Grammar.data.get(c)) {
                    if (m.getAsJsonObject().has(g.name)) {
                        sdp.add(makeLine(c, g, m.getAsJsonObject()));
                    }else if(m.getAsJsonObject().has(g.push)) {
                        Log.d(TAG,"push:"+g.push);

                        JsonElement pushElement = m.getAsJsonObject().get(g.push);
                        if(pushElement.isJsonArray()){
                            JsonArray pushArr = pushElement.getAsJsonArray();
                            for (JsonElement element : pushArr) {
                                JsonObject obj = element.getAsJsonObject();
                                sdp.add(makeLine(c, g, obj));
                            }
                        }else{
                            JsonObject pushObj = pushElement.getAsJsonObject();
                            for (Map.Entry<String, JsonElement> entry : pushObj.entrySet()) {
                                sdp.add(makeLine(c, g, entry.getValue().getAsJsonObject()));
                            }
                        }

                    }
                }
            }

        }

        return String.join(System.lineSeparator(), sdp) + System.lineSeparator();
    }

    public static String makeLine(Character c, Grammar grammar, JsonObject location) {
        Log.d(TAG,"c:"+c);
        Log.d(TAG,"name:"+grammar.name);
        Log.d(TAG,"push:"+grammar.push);
        Log.d(TAG,location.toString());

        JsonElement formatLocation = grammar.push.isEmpty() ? location.get(grammar.name) : location;
        String strFormat = grammar.format.apply(formatLocation);

        strFormat = c + "=" + strFormat;
        ArrayList<Object> formatParameters = new ArrayList<>();

        if (grammar.names.length > 0) {
            for (int i = 0; i < grammar.names.length; i += 1) {
                String n = grammar.names[i];
                if (!grammar.name.isEmpty()) {
                    Log.d(TAG,grammar.name+" "+location.get(grammar.name).toString());
                    formatParameters.add(jsonToReal(location.getAsJsonObject(grammar.name).get(n)));
                }
                else { // for mLine and push attributes
                    if(location.has(grammar.names[i])){
                        formatParameters.add(jsonToReal(location.get(grammar.names[i])));
                    }else{
                        formatParameters.add(0);
                    }
                }
            }
        }
        else {
            formatParameters.add(jsonToReal(location.get(grammar.name)));
        }

        for (Object element : formatParameters) {
            if (element instanceof String) {
                Log.d(TAG,"String: " + element);
            } else if (element instanceof Integer) {
                Log.d(TAG,"Integer: " + element);
            } else if (element instanceof Double) {
                Log.d(TAG,"Double: " + element);
            } else if (element instanceof Boolean) {
                Log.d(TAG,"Boolean: " + element);
            } else {
                Log.d(TAG,"Unknown type: " + element);
            }
        }
        Log.d(TAG,strFormat);
        return customFormat(strFormat, formatParameters);
    }

    private static String customFormat(String format, List<Object> args) {
        StringBuilder result = new StringBuilder();
        int argIndex = 0; // Parameter index

        // Traverse each character in the format string
        for (int i = 0; i < format.length(); i++) {
            char currentChar = format.charAt(i);

            // When encountering '%', check the next character to decide how to handle it
            if (currentChar == '%' && i + 1 < format.length()) {
                char nextChar = format.charAt(i + 1);

                if (nextChar == 's') {
                    // %s for string parameters
                    if (argIndex < args.size()) {
                        Object arg = args.get(argIndex);
                        result.append(arg != null ? arg.toString() : "");  // If the argument is null, replace it with an empty string
                    }
                    argIndex++; // Move to the next argument
                    i++; // Skip the placeholder
                } else if (nextChar == 'd') {
                    // %d for integer parameters
                    if (argIndex < args.size()) {
                        Object arg = args.get(argIndex);
                        if (arg instanceof Long) {
                            result.append(arg);  // Output the integer directly
                        } else {
                            result.append(0);  // If not an integer, output 0 or handle it as default
                        }
                    }
                    argIndex++; // Move to the next argument
                    i++; // Skip the placeholder
                } else if (nextChar == 'v') {
                    // %v is just a placeholder, no output
                    argIndex++; // Skip the argument without output
                    i++; // Skip the placeholder
                } else {
                    // Handle other characters, such as when '%' is followed by something other than 's', 'd', or 'v'
                    result.append(currentChar);
                }
            } else {
                // Non-placeholder character, directly add it to the result
                result.append(currentChar);
            }
        }

        return result.toString();
    }

    private static Object jsonToReal(JsonElement e){
        if(e.isJsonNull()) {
            return null;
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        if(p.isNumber()){
            return p.getAsLong();
        }else if(p.isString()){
            return p.getAsString();
        }
        return 0;
    }
}
