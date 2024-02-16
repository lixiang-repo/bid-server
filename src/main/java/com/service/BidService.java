package com.service;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.io.*;

@Service("BidService")
public class BidService extends BaseService {

    @PostConstruct
    public void init() throws IOException {
        logger = LoggerFactory.getLogger("BidService");
        initStubMap("tfserving.conf");
    }

}
