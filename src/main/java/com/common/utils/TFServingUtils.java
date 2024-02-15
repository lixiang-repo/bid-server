package com.common.utils;

import com.common.model.TFServingFeature;
import com.common.model.VarType;
import google.meiyou.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
            case STR:
                String stringValue = (String) featureValue;
                feature = Feature.newBuilder().setBytesList(
                        BytesList.newBuilder().addValue(ByteString.copyFromUtf8(stringValue))
                ).build();
                break;
            case FLOAT:
                float floatValue = ((Number) featureValue).floatValue();
                feature = Feature.newBuilder().setFloatList(
                        FloatList.newBuilder().addValue(floatValue)
                ).build();
                break;
            case LONG:
                long longValue = ((Number) featureValue).longValue();
                feature = Feature.newBuilder().setInt64List(
                        Int64List.newBuilder().addValue(longValue)
                ).build();
                break;
            case LIST_STR:
                List<String> stringList = (List<String>) featureValue;
                List<ByteString> byteStringList = stringList.parallelStream().map(
                        x -> ByteString.copyFromUtf8(x)
                ).collect(Collectors.toList());

                feature = Feature.newBuilder().setBytesList(
                        BytesList.newBuilder().addAllValue(byteStringList)
                ).build();
                break;
            case LIST_FLOAT:
                List<Number> floatList = (List<Number>) featureValue;
                feature = Feature.newBuilder().setFloatList(
                        FloatList.newBuilder().addAllValue(
                                floatList.parallelStream().map(x -> x.floatValue()).collect(Collectors.toList())
                        )
                ).build();
                break;
            case LIST_LONG:
                List<Number> longList = (List<Number>) featureValue;
                feature = Feature.newBuilder().setInt64List(
                        Int64List.newBuilder().addAllValue(
                                longList.parallelStream().map(x -> x.longValue()).collect(Collectors.toList())
                        )
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
            case LIST_STR:
                List<String> stringList = (List<String>) featureValue;
                features = stringList.parallelStream().map(
                        x -> Feature.newBuilder().setBytesList(BytesList.newBuilder().addValue(ByteString.copyFromUtf8(x))).build()
                ).collect(Collectors.toList());
                break;
            case LIST_FLOAT:
                List<Number> floatList = (List<Number>) featureValue;
                features = floatList.parallelStream().map(
                        x -> Feature.newBuilder().setFloatList(FloatList.newBuilder().addValue(x.floatValue())).build()
                ).collect(Collectors.toList());
                break;
            case LIST_LONG:
                List<Number> longList = (List<Number>) featureValue;
                features = longList.parallelStream().map(
                        x -> Feature.newBuilder().setInt64List(Int64List.newBuilder().addValue(x.longValue())).build()
                ).collect(Collectors.toList());
                break;
            case LIST_LIST_STR:
                List<List<String>> listListStr = (List<List<String>>) featureValue;
                features = listListStr.parallelStream().map(list -> {
                    List<ByteString> byteStringList = list.parallelStream().map(x -> ByteString.copyFromUtf8(x)).collect(Collectors.toList());
                    Feature feature = Feature.newBuilder().setBytesList(BytesList.newBuilder().addAllValue(byteStringList)).build();
                    return feature;
                }).collect(Collectors.toList());
                break;
            case LIST_LIST_FLOAT:
                List<List<Number>> listListFloat = (List<List<Number>>) featureValue;
                features = listListFloat.parallelStream().map(list -> {
                    List<Float> floats = list.parallelStream().map(x -> x.floatValue()).collect(Collectors.toList());
                    Feature feature = Feature.newBuilder().setFloatList(FloatList.newBuilder().addAllValue(floats)).build();
                    return feature;
                }).collect(Collectors.toList());
                break;
            case LIST_LIST_LONG:
                List<List<Number>> listListInt = (List<List<Number>>) featureValue;
                features = listListInt.parallelStream().map(list -> {
                    List<Long> ints = list.parallelStream().map(x -> x.longValue()).collect(Collectors.toList());
                    Feature feature = Feature.newBuilder().setInt64List(Int64List.newBuilder().addAllValue(ints)).build();
                    return feature;
                }).collect(Collectors.toList());
                break;
            default:
                throw new ValueException("不支持的值类型");
        }

        return FeatureList.newBuilder().addAllFeature(features).build();
    }
}
