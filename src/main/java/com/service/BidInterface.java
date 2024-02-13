package com.service;

import com.common.model.UserObject;
import com.common.model.ItemObject;

import java.util.List;

public interface BidInterface {

    List<ItemObject> predict(UserObject userQueryInfo, List<ItemObject> items);

}
