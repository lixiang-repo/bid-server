package com.service;

import com.common.model.VarType;
import com.common.model.*;
import com.common.model.TFServingFeature;
import com.common.utils.*;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Service("BidService")
public class BidService extends BaseService {

    @PostConstruct
    public void init() throws IOException {
        logger = LoggerFactory.getLogger(getClass());
        initSchema("schema.conf");
        initStubMap("tfserving.conf");
    }

    @Override
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
                } else {
                    ctxFeatures.put(name, new TFServingFeature(userMap.getOrDefault(name, "1"), VarType.STR));
                }
            } else if (ctxSchemaSet.contains(name)) { //@ctx
                // hour, display
                String v = StringHelper.fillNa(ctxMap.get(name));
                if (dtypeMap.get(name).equalsIgnoreCase("FLOAT")) {
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

    @Override
    public ItemFeaturesPo getItemFeatures(UserObject userInfo, List<ItemObject> items) {
        ItemFeaturesPo itemFeaturesPo = new ItemFeaturesPo();
        Map<String, TFServingFeature> itemFeatures = new HashMap<>();

        for (String name : itemSchemaSet) {
            //industry, //i_imp_cnt_d90, i_hour_imp_cnt, u_industry_imp_item_cnt_d90
            if (dtypeMap.get(name).equalsIgnoreCase("FLOAT")) {
                List<Float> array = items.parallelStream().map(
                        item -> StringHelper.parseFloat(item.getMap().get(name), -1)
                ).collect(Collectors.toList());
                itemFeatures.put(name, new TFServingFeature(array, VarType.LIST_FLOAT));
            } else if (dtypeMap.get(name).equalsIgnoreCase("STRING")) {
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
