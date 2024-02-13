package com.common.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class UserObject implements Serializable {
    private static final long serialVersionUID = 3470193328421337453L;

    @JSONField(name = "trackingId")
    private String trackingId;

    @JSONField(name = "version")
    private String version;

    @JSONField(name = "uuid")
    private String uuid;

    @JSONField(name = "userMap")
    private Map<String, String> userMap;

    @JSONField(name = "contextMap")
    private Map<String, String> contextMap;

    public UserObject() {}

}
