package com.common.model;

import lombok.Data;

import java.util.*;

@Data
public class ItemFeaturesPo {
    private Map<String, TFServingFeature> features;
}
