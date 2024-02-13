package com.service;

import com.common.model.*;
import com.common.utils.TFServingUtils;
import google.meiyou.protobuf.ByteString;
import org.slf4j.Logger;
import org.tensorflow.framework.DataType;
import org.tensorflow.framework.TensorProto;
import org.tensorflow.framework.TensorShapeProto;
import tensorflow.serving.Predict;
import tensorflow.serving.PredictionServiceGrpc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public abstract class BaseService {
    Logger logger;

    HashMap<String, PredictionServiceGrpc.PredictionServiceBlockingStub> stubMap;

    HashMap<String, String> dtypeMap;

    HashSet<String> userSchemaSet;

    HashSet<String> ctxSchemaSet;

    HashSet<String> itemSchemaSet;

    HashSet<String> userAndctxSchemaSet;

    HashMap<String, Integer> seqLengthMap;

    public void initSchema(String schema) throws IOException {
        dtypeMap = new HashMap<>();

        userSchemaSet = new HashSet<>();
        ctxSchemaSet = new HashSet<>();
        itemSchemaSet = new HashSet<>();
        userAndctxSchemaSet = new HashSet<>();

        seqLengthMap = new HashMap<>();

        InputStream fileInputStream = getClass().getClassLoader().getResourceAsStream(schema);
        BufferedReader br = new BufferedReader(new InputStreamReader(fileInputStream));
        String line;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("#") || line.startsWith("label")) {
                continue;
            }
            String name = line.split(" +")[0];
            String dType = line.split(" +")[1];
            dtypeMap.put(name, dType);
            if (line.contains("@user")) {
                userSchemaSet.add(name);
            } else if (line.contains("@ctx")) {
                ctxSchemaSet.add(name);
            } else if (line.contains("@item")){
                itemSchemaSet.add(name);
            } else {
                throw new IOException("@user @ctx @item must in line!");
            }
            if (dType.contains("ARRAY")) {
                if (dType.contains("(")) {
                    //ARRAY<STRING>(10)
                    int length = Integer.parseInt(dType.split("\\(")[1].replace(")", ""));
                    seqLengthMap.put(name, length);
                } else {
                    seqLengthMap.put(name, 50);
                }
            }
        }
        userAndctxSchemaSet.addAll(userSchemaSet);
        userAndctxSchemaSet.addAll(ctxSchemaSet);
        logger.info("userSchemaSet: " + userSchemaSet);
        logger.info("ctxSchemaSet: " + ctxSchemaSet);
        logger.info("itemSchemaSet: " + itemSchemaSet);
        logger.info("userAndctxSchemaSet: " + userAndctxSchemaSet);
        logger.info("dtypeMap: " + dtypeMap);
        logger.info("seqLengthMap: " + seqLengthMap);

    }

    public void initStubMap(String tfserving) throws IOException {
        //int tfserving
        InputStream fileInputStream = getClass().getClassLoader().getResourceAsStream(tfserving);
        Properties properties = new Properties();
        properties.load(fileInputStream);
        stubMap = new HashMap<>();

        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = entry.getKey().toString();
            String hosts = entry.getValue().toString();

            String host = hosts.split(":")[0];
            int port = Integer.parseInt(hosts.split(":")[1]);
            PredictionServiceGrpc.PredictionServiceBlockingStub stub = TFServingUtils.getPredictionServiceBlockingStub(host, port);
            stubMap.put(key, stub);

            logger.info(String.format("bid-server init %s hosts:%s", key, hosts));
        }
    }

    public abstract UserFeaturesPo getCtxFeatures(UserObject userInfo);

    public abstract ItemFeaturesPo getItemFeatures(UserObject userInfo, List<ItemObject> items);

    public List<ItemObject> predict(UserObject userInfo, List<ItemObject> items) {
        long time1 = System.currentTimeMillis();
        UserFeaturesPo userFeaturesPo = getCtxFeatures(userInfo);
        Map<String, TFServingFeature> ctxFeatures = userFeaturesPo.getCtxFeatures();
        long time2 = System.currentTimeMillis();
        ItemFeaturesPo itemFeaturesPo = getItemFeatures(userInfo, items);
        long time3 = System.currentTimeMillis();
        Map<String, TFServingFeature> seqFeatures = itemFeaturesPo.getFeatures();

        ByteString inputByteStr = TFServingUtils.BuildSeqExampleByteString(ctxFeatures, seqFeatures);
        TensorShapeProto inputTensorShape = TensorShapeProto.newBuilder().
                addDim(TensorShapeProto.Dim.newBuilder().setSize(1)).
                build();

        TensorProto inputProto = TensorProto.newBuilder()
                .addStringVal(inputByteStr)
                .setTensorShape(inputTensorShape)
                .setDtype(DataType.DT_STRING)
                .build();

        ByteString recordTypeByteStr = ByteString.copyFromUtf8("SequenceExample");

        TensorProto recordTypeProto = TensorProto.newBuilder()
                .addStringVal(recordTypeByteStr)
                .setDtype(DataType.DT_STRING)
                .build();

        Predict.PredictRequest.Builder predictRequestBuilder = TFServingUtils.getPredictRequestBuilder("model", "pred");
        predictRequestBuilder.putInputs("record_type", recordTypeProto);
        predictRequestBuilder.putInputs("input", inputProto);

        long time4 = System.currentTimeMillis();
        try{
            if (! stubMap.containsKey(userInfo.getVersion())) {
                logger.error(String.format("bidserver version:%s is error", userInfo.getVersion()));
            } else {
                PredictionServiceGrpc.PredictionServiceBlockingStub stub = stubMap.get(userInfo.getVersion());
                Predict.PredictResponse response = stub.predict(predictRequestBuilder.build());

                if (response == null) {
                    logger.error("response is null! userinfo: " + userInfo.toString() + "items: " + items.toString());
                } else {
                    List<Float> scores = response.getOutputsOrThrow("output").getFloatValList();
                    if (scores == null || scores.size() < items.size()){
                        logger.error("scores is null or error! userinfo: " + userInfo.toString() + "items: " + items.toString());
                    } else {
                        int scoreSize = scores.size();
                        int itemSize = items.size();

                        for (int i = 0; i < itemSize; i++) {
                            ItemObject item = items.get(i);
                            double pctr = scores.get(i);
                            double pcvr = itemSize + i < scoreSize? scores.get(itemSize + i): 0.0;
                            double ecpm = item.getCpa() > 0? pctr * pcvr * item.getCpa() * 1000: pctr * item.getCpc() * 1000;

                            item.setPctr(pctr);
                            item.setPcvr(pcvr);
                            item.setEcpm(ecpm);

                        }
                    }
                }
            }

        } catch (Exception e){
            logger.error("bid-server Exception: " + e + " userinfo: " + userInfo.toString() + " items: " + items.toString());
        }

        // 按权重排序
        //items.sort((x1, x2) -> -Double.compare(x1.getWeight(), x2.getWeight()));

        long time5 = System.currentTimeMillis();
        logger.info(String.format(
                "uuid:%s, item_size:%s, user_feature cost time:%d ms,  item_feature cost time:%d ms, " +
                        "tfrecord cost time:%d ms, tf serving cost time:%d ms, total time:%d ms",
                userInfo.getUuid(), items.size(), time2 - time1, time3 - time2,
                time4 - time3, time5 - time4, time5 - time1
        ));
        return items;
    }

}
