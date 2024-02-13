package com.common.utils;

import com.common.model.TFServingFeature;
import com.common.model.VarType;
import google.meiyou.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
//import org.tensorflow.protobuf.ByteString;
//import org.tensorflow.grpc.ManagedChannel;
//import org.tensorflow.grpc.ManagedChannelBuilder;
import jdk.nashorn.internal.runtime.regexp.joni.exception.ValueException;
import org.tensorflow.example.*;
import tensorflow.serving.Model;
import tensorflow.serving.Predict;
import tensorflow.serving.PredictionServiceGrpc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TFServingUtils {
    public static PredictionServiceGrpc.PredictionServiceBlockingStub getPredictionServiceBlockingStub(String ip, int port){
        ManagedChannel channel = ManagedChannelBuilder.forAddress(ip, port).usePlaintext(true).build();
        return PredictionServiceGrpc.newBlockingStub(channel);
    }

    public static Predict.PredictRequest.Builder getPredictRequestBuilder(String modelName, String signature){
        Predict.PredictRequest.Builder predictRequestBuilder = Predict.PredictRequest.newBuilder();
        Model.ModelSpec.Builder modelSpecBuilder = Model.ModelSpec.newBuilder();
        modelSpecBuilder.setName(modelName);
        modelSpecBuilder.setSignatureName(signature);
        predictRequestBuilder.setModelSpec(modelSpecBuilder);
        return  predictRequestBuilder;
    }

    public static ByteString buildExampleByteString(Map<String, TFServingFeature> objectMap){
        Map<String, Feature> featureMap = new HashMap<>();
        for (Map.Entry<String, TFServingFeature> kv: objectMap.entrySet()){
            String k = kv.getKey();
            TFServingFeature v = kv.getValue();
            Feature feature = buildFeature(v);

            featureMap.put(k, feature);
        }

        Features features = Features.newBuilder().putAllFeature(featureMap).build();
        return Example.newBuilder().setFeatures(features).build().toByteString();
    }

    public static ByteString BuildSeqExampleByteString(Map<String, TFServingFeature> ctxTfFeatures,
                                                       Map<String, TFServingFeature> seqTfFeatures){
        Map<String, Feature> ctxFeatureMap = new HashMap<>();
        for (Map.Entry<String, TFServingFeature> kv: ctxTfFeatures.entrySet()){
            String k = kv.getKey();
            TFServingFeature v = kv.getValue();
            Feature feature = buildFeature(v);

            ctxFeatureMap.put(k, feature);
        }

        Features ctxFeatures = Features.newBuilder().putAllFeature(ctxFeatureMap).build();

        Map<String, FeatureList> seqFeatureMap = new HashMap<>();
        for (Map.Entry<String, TFServingFeature> kv: seqTfFeatures.entrySet()){
            String k = kv.getKey();
            TFServingFeature v = kv.getValue();
            FeatureList featureList = buildFeatureList(v);
            seqFeatureMap.put(k, featureList);
        }

        FeatureLists seqFeatures = FeatureLists.newBuilder().putAllFeatureList(seqFeatureMap).build();

        return SequenceExample.newBuilder().
                setContext(ctxFeatures).
                setFeatureLists(seqFeatures).
                build().toByteString();
    }

    private static Feature buildFeature(TFServingFeature tfServingFeature){
        Feature feature;
        Object featureValue = tfServingFeature.getFeature();
        VarType featureType = tfServingFeature.getFeatureType();
        switch (featureType){
            case INT:
            case LONG:
                long number = ((Number)featureValue).longValue();
                feature = Feature.newBuilder().setInt64List(
                        Int64List.newBuilder().addValue(number)
                ).build();
                break;
            case FLOAT:
                float num = ((Number)featureValue).floatValue();
                feature = Feature.newBuilder().setFloatList(
                        FloatList.newBuilder().addValue(num)
                ).build();
                break;
            case STR:
                String str = (String)featureValue;
                feature = Feature.newBuilder().setBytesList(
                        BytesList.newBuilder().addValue(ByteString.copyFromUtf8(str))
                ).build();
                break;
            case LIST_INT:
            case LIST_LONG:
                List<Object> numList = (List)featureValue;
                List<Long> longList = numList.parallelStream().map(
                                x -> ((Number)x).longValue()
                        ).collect(Collectors.toList());
                feature = Feature.newBuilder().setInt64List(
                        Int64List.newBuilder().addAllValue(longList)
                ).build();
                break;
            case LIST_STR:
                List<Object> strlist = (List)featureValue;
                List<ByteString> byteStringList = strlist.parallelStream().map(
                        x -> ByteString.copyFromUtf8((String)x)
                ).collect(Collectors.toList());

                feature = Feature.newBuilder().setBytesList(
                        BytesList.newBuilder().addAllValue(byteStringList)
                ).build();
                break;
            case LIST_FLOAT:
                List<Object> floatlist = (List)featureValue;
                List<Float> floatList =  floatlist.parallelStream().map(
                                x -> ((Number)x).floatValue()
                        ).collect(Collectors.toList());

                feature = Feature.newBuilder().setFloatList(
                        FloatList.newBuilder().addAllValue(floatList)
                ).build();
                break;
            default:
                throw new ValueException("不支持的值类型");
        }

        return feature;
    }

    private static FeatureList buildFeatureList(TFServingFeature tfServingFeature){
        List<Feature> features = new ArrayList<>();
        Object featureValue = tfServingFeature.getFeature();
        VarType featureType = tfServingFeature.getFeatureType();
        switch (featureType){
            case LIST_INT:
            case LIST_LONG:
                List<Object> numList = (List)featureValue;
                features = numList.parallelStream().map(
                                x -> Feature.newBuilder().setInt64List(Int64List.newBuilder().addValue(((Number)x).longValue())).build()
                        ).collect(Collectors.toList());
                break;
            case LIST_FLOAT:
                List<Object> fList = (List)featureValue;
                features = fList.parallelStream().map(
                                x -> Feature.newBuilder().setFloatList(FloatList.newBuilder().addValue(((Number)x).floatValue())).build()
                        ).collect(Collectors.toList());
                break;
            case LIST_STR:
                List<Object> strlist = (List)featureValue;
                features = strlist.parallelStream().map(
                                x -> Feature.newBuilder().setBytesList(BytesList.newBuilder().addValue(ByteString.copyFromUtf8((String)x))).build()
                        ).collect(Collectors.toList());
                break;
            case LIST_LIST_STR:
                List<Object> strlist_list = (List)featureValue;
                for (Object v: strlist_list){
                    List<Object> str_list = (List)v;
                    List<ByteString> list_bytes = str_list.parallelStream().map(
                                    x -> ByteString.copyFromUtf8((String)x)
                            ).collect(Collectors.toList());

                    Feature feature = Feature.newBuilder()
                            .setBytesList(BytesList.newBuilder().addAllValue(list_bytes))
                            .build();

                    features.add(feature);
                }
                break;
            default:
                throw new ValueException("不支持的值类型");
        }

        return FeatureList.newBuilder().addAllFeature(features).build();
    }
}
