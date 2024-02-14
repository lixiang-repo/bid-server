package com.common.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class QueryParams {

    private UserObject userInfo;

    private Map<String, List<ItemObject>> map;

    public List<ItemObject> getItems() {
        if (map == null || map.get("recall") == null || map.get("recall").size() == 0) {
            ArrayList<ItemObject> items = new ArrayList<>();
            ItemObject item = new ItemObject(new HashMap<>());
            items.add(item);
            return items;
        }
        return map.get("recall");
    }

}
