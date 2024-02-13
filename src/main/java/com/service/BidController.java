package com.service;

import com.common.model.UserObject;
import com.common.model.ItemObject;
import com.common.model.QueryParams;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/bidserver")
public class BidController {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource(name = "BidService")
    private BidInterface rankingService;
    
    @RequestMapping("/rank")
    public List<ItemObject> getRankingList(@RequestBody QueryParams queryObj){
        UserObject userInfo = queryObj.getUserInfo();

        if (userInfo == null){
            logger.error("bid-server, userinfo is null");
            return null;
        }

        String uuid = userInfo.getUuid();
        if (StringUtils.isBlank(uuid)){
            logger.info("bid-server, 用户ID为空: " + userInfo);
            return null;
        }

        List<ItemObject> items = queryObj.getItems();
        if (items == null || items.size() == 0){
            logger.info("bid-server, items size=0");
            return null;
        }

        List<ItemObject> results = rankingService.predict(userInfo, items);
        if (results == null || results.size() == 0 || results.size() != items.size()) {
            logger.info("bid-server, 异常, 结果为空 " + results);
        }

        return results;
    }

    @RequestMapping("/alive")
    public String checkAlive(){
        return "hello bid-server!";
    }
}
