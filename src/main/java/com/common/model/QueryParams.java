package com.common.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class QueryParams {

    private UserObject userInfo;

    private Map<String, List<ItemObject>> map;

    public List<ItemObject> getItems() {
        return map.getOrDefault("recall", new ArrayList<ItemObject>());
    }

}
