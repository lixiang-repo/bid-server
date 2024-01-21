package com.common.utils;

import com.common.model.VarType;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TFServingFeature {
    private Object feature;
    private VarType featureType;
}
