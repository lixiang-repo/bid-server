package com.common.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class QueryParams {

    private UserObject userInfo;

    private Map<String, List<ItemObject>> map;

    public List<ItemObject> getItems(List<ItemObject> defaultItems) {
        if (map == null || map.get("recall") == null || map.get("recall").size() == 0) {
            return defaultItems;
        }
        return map.get("recall");
    }

}
