package org.json;

import java.util.*;

public class JSONObject {
    final Map<String, Object> map = new LinkedHashMap<>();

    public JSONObject() {}

    public JSONObject(String json) {
        Parser p = new Parser(json.trim());
        p.expect('{');
        map.putAll(p.parseObjectBody());
    }

    JSONObject(Map<String, Object> m) { map.putAll(m); }

    public String getString(String key) {
        Object v = map.get(key);
        if (v == null) throw new RuntimeException("Key not found: " + key);
        return v.toString();
    }
    public int getInt(String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }
    public double getDouble(String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(v.toString());
    }
    public JSONObject getJSONObject(String key) {
        Object v = map.get(key);
        if (v instanceof JSONObject jo) return jo;
        throw new RuntimeException("Not JSONObject: " + key);
    }
    public JSONArray getJSONArray(String key) {
        Object v = map.get(key);
        if (v instanceof JSONArray ja) return ja;
        throw new RuntimeException("Not JSONArray: " + key);
    }
    public boolean has(String key) { return map.containsKey(key); }
    public String optString(String key, String def) { Object v = map.get(key); return v!=null?v.toString():def; }
    public void put(String key, Object value) { map.put(key, value); }

    static class Parser {
        final String s; int pos;
        Parser(String s) { this.s=s; this.pos=0; }
        void skipWs() { while(pos<s.length()&&s.charAt(pos)<=' ')pos++; }
        char peek() { skipWs(); return pos<s.length()?s.charAt(pos):0; }
        char consume() { skipWs(); if(pos>=s.length())throw new RuntimeException("EOF"); return s.charAt(pos++); }
        void expect(char c) { char g=consume(); if(g!=c)throw new RuntimeException("Expected '"+c+"' got '"+g+"'"); }

        Map<String,Object> parseObjectBody() {
            Map<String,Object> m=new LinkedHashMap<>();
            skipWs(); if(peek()=='}'){pos++;return m;}
            while(true){
                skipWs(); String key=parseString();
                skipWs(); expect(':');
                Object val=parseValue(); m.put(key,val);
                skipWs(); char ch=consume();
                if(ch=='}')break; if(ch!=',')throw new RuntimeException("Expected , or }");
            }
            return m;
        }
        JSONArray parseArrayBody() {
            JSONArray arr=new JSONArray();
            skipWs(); if(peek()==']'){pos++;return arr;}
            while(true){
                arr.addRaw(parseValue()); skipWs(); char ch=consume();
                if(ch==']')break; if(ch!=',')throw new RuntimeException("Expected , or ]");
            }
            return arr;
        }
        Object parseValue() {
            char c=peek();
            if(c=='{'){pos++;return new JSONObject(parseObjectBody());}
            if(c=='['){pos++;return parseArrayBody();}
            if(c=='"')return parseString();
            if(c=='t'){pos+=4;return Boolean.TRUE;}
            if(c=='f'){pos+=5;return Boolean.FALSE;}
            if(c=='n'){pos+=4;return null;}
            return parseNumber();
        }
        String parseString() {
            expect('"'); StringBuilder sb=new StringBuilder();
            while(pos<s.length()){
                char c=s.charAt(pos++);
                if(c=='"')return sb.toString();
                if(c=='\\'){
                    char e=s.charAt(pos++);
                    switch(e){
                        case '"'->sb.append('"'); case '\\'->sb.append('\\'); case '/'->sb.append('/');
                        case 'n'->sb.append('\n'); case 'r'->sb.append('\r'); case 't'->sb.append('\t');
                        case 'u'->{if(pos+4<=s.length()){try{sb.append((char)Integer.parseInt(s.substring(pos,pos+4),16));}catch(NumberFormatException ignored){sb.append('?');}pos+=4;}}
                        default->sb.append(e);
                    }
                }else sb.append(c);
            }
            throw new RuntimeException("Unterminated string");
        }
        Number parseNumber() {
            skipWs(); int start=pos;
            if(pos<s.length()&&s.charAt(pos)=='-')pos++;
            while(pos<s.length()&&Character.isDigit(s.charAt(pos)))pos++;
            if(pos<s.length()&&s.charAt(pos)=='.'){pos++;while(pos<s.length()&&Character.isDigit(s.charAt(pos)))pos++;}
            if(pos<s.length()&&(s.charAt(pos)=='e'||s.charAt(pos)=='E')){pos++;if(pos<s.length()&&(s.charAt(pos)=='+'||s.charAt(pos)=='-'))pos++;while(pos<s.length()&&Character.isDigit(s.charAt(pos)))pos++;}
            String num=s.substring(start,pos);
            if(num.contains(".")||num.contains("e")||num.contains("E"))return Double.parseDouble(num);
            try{return Long.parseLong(num);}catch(NumberFormatException ex){return Double.parseDouble(num);}
        }
    }
}
