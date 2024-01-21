package com.common.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;


public class StringHelper {
    public static int parseInt(String s, int defaultValue){
        int ret = defaultValue;

        try{
            ret = Integer.parseInt(s);
        } catch (Exception e){

        }

        return ret;
    }

    public static long parseLong(String s, long defaultValue){
        long ret = defaultValue;

        try{
            ret = Long.parseLong(s);
        } catch (Exception e){

        }

        return ret;
    }

    public static float parseFloat(String s, float defaultValue){
        float ret = defaultValue;

        try{
            ret = Float.parseFloat(s);
        } catch (Exception e){

        }

        return ret;
    }

    public static List<String> getTfList(String str, String sep, int len, String dv){
        String[] rapper = str.split(sep);
        if(rapper.length == 1 && rapper[0].length() == 0){
            rapper[0] = dv;
        }
        ArrayList<String> result = new ArrayList<>(rapper.length);
        Collections.addAll(result, rapper);
        int diff = len - result.size();
        for(int i=0; i < diff; i++){
            result.add(dv);
        }
        return result.subList(0, len);
    };


}
