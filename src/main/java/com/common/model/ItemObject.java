package com.common.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class ItemObject implements Comparable<ItemObject>, Serializable {
    private static final long serialVersionUID = -3133000493546506575L;

    @JSONField(
            name = "map"
    )
    private Map<String, String> map;

    @JSONField(
            name = "pctr"
    )
    private double pctr;

    @JSONField(
            name = "pcvr"
    )
    private double pcvr;

    @JSONField(
            name = "cpc"
    )
    private double cpc;

    @JSONField(
            name = "ecpm"
    )
    private double ecpm;

    @JSONField(
            name = "ocpc"
    )
    private double ocpc;

    @JSONField(
            name = "ocpm"
    )
    private double ocpm;

    @JSONField(
            name = "weight"
    )
    private double weight;

    public ItemObject() {}

    @Override
    public int compareTo(ItemObject o) {
        return Double.compare(o.ecpm, this.ecpm);
    }

}
