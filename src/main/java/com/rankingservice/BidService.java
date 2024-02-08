package com.rankingservice;

import com.common.model.VarType;
import com.common.model.*;
import com.common.utils.TFServingFeature;
import com.common.utils.TFServingUtils;
import com.common.utils.*;
import google.meiyou.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.tensorflow.framework.DataType;
import org.tensorflow.framework.TensorProto;
import org.tensorflow.framework.TensorShapeProto;
import tensorflow.serving.Predict;
import tensorflow.serving.PredictionServiceGrpc;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service("BidService")
public class BidService implements RankingService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    HashMap<String, PredictionServiceGrpc.PredictionServiceBlockingStub> stubMap;

    HashMap<String, String> schemaDtypeMap;

    HashSet<String> userSchemaSet;

    HashSet<String> ctxSchemaSet;

    HashSet<String> itemSchemaSet;

    HashSet<String> userAndctxSchemaSet;

    HashMap<String, Integer> seqLengthMap;

    public void initSchema() throws IOException {
        schemaDtypeMap = new HashMap<>();

        userSchemaSet = new HashSet<>();
        ctxSchemaSet = new HashSet<>();
        itemSchemaSet = new HashSet<>();
        userAndctxSchemaSet = new HashSet<>();

        seqLengthMap = new HashMap<>();

        InputStream fileInputStream = getClass().getClassLoader().getResourceAsStream("schema.conf");
        BufferedReader br = new BufferedReader(new InputStreamReader(fileInputStream));
        String line;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("#") || line.startsWith("label")) {
                continue;
            }
            String name = line.split(" +")[0];
            String dType = line.split(" +")[1];
            schemaDtypeMap.put(name, dType);
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
        System.out.println("userSchemaSet: " + userSchemaSet);
        System.out.println("ctxSchemaSet: " + ctxSchemaSet);
        System.out.println("itemSchemaSet: " + itemSchemaSet);
        System.out.println("userAndctxSchemaSet: " + userAndctxSchemaSet);
        System.out.println("schemaDtypeMap: " + schemaDtypeMap);
        System.out.println("seqLengthMap: " + seqLengthMap);

    }

    public void initStubMap() throws IOException {
        //int tfserving
        InputStream fileInputStream = getClass().getClassLoader().getResourceAsStream("tfserving.conf");
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

    @PostConstruct
    public void init() throws IOException {
        initSchema();
        initStubMap();
    }

    @Override
    public List<ItemObject> rank(UserObject userInfo, List<ItemObject> items) {
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

    private UserFeaturesPo getCtxFeatures(UserObject userInfo) {
        UserFeaturesPo userFeaturesPo = new UserFeaturesPo();
        Map<String, TFServingFeature> ctxFeatures = new HashMap<>();

        Map<String, String> userMap = userInfo.getUserMap();
        Map<String, String> ctxMap = userInfo.getContextMap();
        for (String name : userAndctxSchemaSet) {
            if (userSchemaSet.contains(name)) { // @user
                // u_imp_cnt_d90, u_advertiser_id_imp_cnt_d90(advertiser_id注意下划线)
                if (schemaDtypeMap.get(name).contains("ARRAY")) {
                    List<String> tfList = StringHelper.getTfList(userMap.getOrDefault(name, ""), ",", seqLengthMap.get(name), "_");
                    ctxFeatures.put(name, new TFServingFeature(tfList, VarType.LIST_STR));
                } else {
                    ctxFeatures.put(name, new TFServingFeature(userMap.getOrDefault(name, "1"), VarType.STR));
                }
            } else if (ctxSchemaSet.contains(name)) { //@ctx
                // hour, display
                String v = StringHelper.fillNa(ctxMap.get(name));
                if (schemaDtypeMap.get(name).equalsIgnoreCase("FLOAT")) {
                    ctxFeatures.put(name, new TFServingFeature(StringHelper.parseFloat(v, -1), VarType.FLOAT));
                } else {
                    ctxFeatures.put(name, new TFServingFeature(v, VarType.STR));
                }
            }

        }

        ctxFeatures.put("label", new TFServingFeature(1.0, VarType.FLOAT));
        ctxFeatures.put("label1", new TFServingFeature(1.0, VarType.FLOAT));
        ctxFeatures.put("label2", new TFServingFeature(1.0, VarType.FLOAT));
        userFeaturesPo.setCtxFeatures(ctxFeatures);
        return userFeaturesPo;
    }


    private ItemFeaturesPo getItemFeatures(UserObject userInfo, List<ItemObject> items) {
        ItemFeaturesPo itemFeaturesPo = new ItemFeaturesPo();
        Map<String, TFServingFeature> itemFeatures = new HashMap<>();

        for (String name : itemSchemaSet) {
            //industry, //i_imp_cnt_d90, i_hour_imp_cnt, u_industry_imp_item_cnt_d90
            if (schemaDtypeMap.get(name).equalsIgnoreCase("FLOAT")) {
                List<Float> array = items.parallelStream().map(
                        item -> StringHelper.parseFloat(item.getMap().get(name), -1)
                ).collect(Collectors.toList());
                itemFeatures.put(name, new TFServingFeature(array, VarType.LIST_FLOAT));
            } else if (schemaDtypeMap.get(name).equalsIgnoreCase("STRING")) {
                List<String> array = items.parallelStream().map(
                        item -> StringHelper.fillNa(item.getMap().get(name))
                ).collect(Collectors.toList());
                itemFeatures.put(name, new TFServingFeature(array, VarType.LIST_STR));
            }

        }
        itemFeaturesPo.setFeatures(itemFeatures);
        return itemFeaturesPo;

    }

}
