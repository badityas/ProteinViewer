package org.json;

import java.util.*;

public class JSONArray implements Iterable<Object> {
    private final List<Object> list = new ArrayList<>();

    public JSONArray() {}
    public JSONArray(String json) {
        String t=json.trim();
        if(!t.startsWith("["))throw new RuntimeException("Not array");
        JSONObject.Parser p=new JSONObject.Parser(t.substring(1));
        JSONArray arr=p.parseArrayBody();
        list.addAll(arr.list);
    }

    void addRaw(Object v) { list.add(v); }
    public void put(Object v) { list.add(v); }
    public int length() { return list.size(); }
    public boolean isEmpty() { return list.isEmpty(); }
    public Object get(int i) { return list.get(i); }
    public JSONObject getJSONObject(int i) {
        Object v=list.get(i);
        if(v instanceof JSONObject jo)return jo;
        throw new RuntimeException("Not JSONObject at "+i);
    }
    public JSONArray getJSONArray(int i) {
        Object v=list.get(i);
        if(v instanceof JSONArray ja)return ja;
        throw new RuntimeException("Not JSONArray at "+i);
    }
    public String getString(int i) { Object v=list.get(i); return v!=null?v.toString():null; }
    public int getInt(int i) { Object v=list.get(i); if(v instanceof Number n)return n.intValue(); return Integer.parseInt(v.toString()); }
    @Override public Iterator<Object> iterator() { return list.iterator(); }
    @Override public String toString() { return list.toString(); }
}
