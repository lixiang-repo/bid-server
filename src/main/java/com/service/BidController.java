package com.service;

import com.common.model.UserObject;
import com.common.model.ItemObject;
import com.common.model.QueryParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/bidserver")
public class BidController {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource(name = "BidService")
    private BaseService baseService;

    private List<ItemObject> defaultItems;

    @PostConstruct
    public List<ItemObject> setDefaultItems() {
        defaultItems = new ArrayList<>(1);
        ItemObject item = new ItemObject();
        item.setMap(new HashMap<>());
        defaultItems.add(item);

        return defaultItems;
    }

    @RequestMapping("/rank")
    public List<ItemObject> predict(@RequestBody QueryParams queryObj){
        UserObject userInfo = queryObj.getUserInfo();
        List<ItemObject> items = queryObj.getItems(defaultItems);

        if (userInfo == null || items == null || items.size() == 0) {
            logger.error("bid-server, queryObj is error" + queryObj);
            return null;
        }

        List<ItemObject> results = baseService.predict(userInfo, items);
        if (results == null || results.size() == 0 || results.size() != items.size()) {
            logger.error("bid-server, 异常, 结果为空 " + results);
        }

        return results;
    }

    @RequestMapping("/alive")
    public String checkAlive(){
        return "hello bid-server!";
    }
}
