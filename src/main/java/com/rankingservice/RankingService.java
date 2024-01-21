package com.rankingservice;

import com.common.model.UserObject;
import com.common.model.ItemObject;

import java.util.List;

public interface RankingService {

    List<ItemObject> rank(UserObject userQueryInfo, List<ItemObject> items);

}
