package com.common.model;

import lombok.Data;

import java.util.Map;

@Data
public class UserFeaturesPo {
    private Map<String, TFServingFeature> ctxFeatures;

}
