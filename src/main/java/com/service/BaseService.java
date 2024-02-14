package com.service;

import com.common.model.*;
import com.common.utils.StringHelper;
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
import java.util.stream.Collectors;

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

    public UserFeaturesPo getCtxFeatures(UserObject userInfo) {
        UserFeaturesPo userFeaturesPo = new UserFeaturesPo();
        Map<String, TFServingFeature> ctxFeatures = new HashMap<>();

        Map<String, String> userMap = userInfo.getUserMap();
        Map<String, String> ctxMap = userInfo.getContextMap();
        for (String name : userAndctxSchemaSet) {
            if (userSchemaSet.contains(name)) { // @user
                // u_imp_cnt_d90, u_advertiser_id_imp_cnt_d90(advertiser_id注意下划线)
                if (dtypeMap.get(name).contains("ARRAY")) {
                    List<String> tfList = StringHelper.getTfList(userMap.getOrDefault(name, ""), ",", seqLengthMap.get(name), "_");
                    ctxFeatures.put(name, new TFServingFeature(tfList, VarType.LIST_STR));
                } else if (dtypeMap.get(name).equalsIgnoreCase("STRING")) {
                    ctxFeatures.put(name, new TFServingFeature(userMap.getOrDefault(name, "1"), VarType.STR));
                } else if (dtypeMap.get(name).equalsIgnoreCase("FLOAT")) {
                    ctxFeatures.put(name, new TFServingFeature(StringHelper.parseFloat(userMap.get(name), -1), VarType.FLOAT));
                }

            } else if (ctxSchemaSet.contains(name)) { //@ctx
                // hour, display
                String v = StringHelper.fillNa(ctxMap.get(name));
                if (dtypeMap.get(name).contains("ARRAY")) {
                    List<String> tfList = StringHelper.getTfList(v, ",", seqLengthMap.get(name), "_");
                    ctxFeatures.put(name, new TFServingFeature(tfList, VarType.LIST_STR));
                } else if (dtypeMap.get(name).equalsIgnoreCase("STRING")) {
                    ctxFeatures.put(name, new TFServingFeature(v, VarType.STR));
                } else if (dtypeMap.get(name).equalsIgnoreCase("FLOAT")) {
                    ctxFeatures.put(name, new TFServingFeature(StringHelper.parseFloat(v, -1), VarType.FLOAT));
                }
            }

        }

        ctxFeatures.put("label", new TFServingFeature(1.0, VarType.FLOAT));
        ctxFeatures.put("label1", new TFServingFeature(1.0, VarType.FLOAT));
        ctxFeatures.put("label2", new TFServingFeature(1.0, VarType.FLOAT));
        userFeaturesPo.setCtxFeatures(ctxFeatures);
        return userFeaturesPo;
    }

    public ItemFeaturesPo getItemFeatures(UserObject userInfo, List<ItemObject> items) {
        ItemFeaturesPo itemFeaturesPo = new ItemFeaturesPo();
        Map<String, TFServingFeature> itemFeatures = new HashMap<>();

        for (String name : itemSchemaSet) {
            //industry, //i_imp_cnt_d90, i_hour_imp_cnt, u_industry_imp_item_cnt_d90
            if (dtypeMap.get(name).contains("ARRAY")) {
                List<List<String>> array = items.parallelStream().map(
                        item -> StringHelper.getTfList(StringHelper.fillNa(item.getMap().get(name)), ",", seqLengthMap.get(name), "_")
                ).collect(Collectors.toList());
                itemFeatures.put(name, new TFServingFeature(array, VarType.LIST_LIST_STR));
            } else if (dtypeMap.get(name).equalsIgnoreCase("STRING")) {
                List<String> array = items.parallelStream().map(
                        item -> StringHelper.fillNa(item.getMap().get(name))
                ).collect(Collectors.toList());
                itemFeatures.put(name, new TFServingFeature(array, VarType.LIST_STR));
            } else if (dtypeMap.get(name).equalsIgnoreCase("FLOAT")) {
                List<Float> array = items.parallelStream().map(
                        item -> StringHelper.parseFloat(item.getMap().get(name), -1)
                ).collect(Collectors.toList());
                itemFeatures.put(name, new TFServingFeature(array, VarType.LIST_FLOAT));
            }

        }
        itemFeaturesPo.setFeatures(itemFeatures);
        return itemFeaturesPo;

    }

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
                    List<Float> scores1 = response.getOutputsOrThrow("output1").getFloatValList();
                    TensorProto tensor2 = response.getOutputsOrDefault("output2", null);
                    List<Float> scores2 = null;
                    if (tensor2 != null) {
                        scores2 = tensor2.getFloatValList();
                    }

                    int itemSize = items.size();
                    if (scores1 == null || scores1.size() != itemSize || (scores2 != null && scores2.size() != itemSize)){
                        logger.error("scores is null or error! userinfo: " + userInfo.toString() + "items: " + items.toString());
                    } else {

                        for (int i = 0; i < itemSize; i++) {
                            ItemObject item = items.get(i);
                            double pctr = scores1.get(i);
                            double pcvr = scores2 != null? scores2.get(i): 0.0;
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
