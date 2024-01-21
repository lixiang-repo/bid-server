package com.common.utils;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;


public class CodisHelper {
    private static final Logger logger = LoggerFactory.getLogger(CodisHelper.class);
    public static String redisHost="r-8vbgtgtgs00vdu4aqx.redis.zhangbei.rds.aliyuncs.com:6379";
    private static Properties properties = new Properties();
    static {
        try {
            properties.load(new InputStreamReader(
                    CodisHelper.class.getClassLoader().getResourceAsStream("tfserving.conf"),
                    "UTF-8"));
            String profile = properties.getProperty("spring.profiles.active");
            String envFile = "application-" + profile + ".properties";
            Properties envProps = new Properties();
            envProps.load(new InputStreamReader(
                    CodisHelper.class.getClassLoader().getResourceAsStream(envFile),
                    "UTF-8"));
            redisHost=envProps.getProperty("recommend.taqu.redis.host");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static ShardedJedisPool pool = null;
    static {
        //logger.info("ycc1"+redisHost);
        String[] hostAndPorts = redisHost.split(",");
        List<JedisShardInfo> jedisShardInfoList = new ArrayList<>(hostAndPorts.length);
        for(String hostAndPort : hostAndPorts) {
            String[] tmp = hostAndPort.split(":");
            JedisShardInfo jedisShardInfo = new JedisShardInfo(tmp[0], Integer.parseInt(tmp[1]), 10000);
            jedisShardInfoList.add(jedisShardInfo);
        }
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxTotal(1000);
        poolConfig.setMaxIdle(20);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTestOnReturn(true);
        pool = new ShardedJedisPool(poolConfig, jedisShardInfoList);
    }

    public static List<Long> getHiidList(String userId) {
        List<Long> hiid = new ArrayList<>();
        ShardedJedis jedis = null;
        try {
            jedis = pool.getResource();
            String h = jedis.get("ac:h_"+userId);
            if(h!=null){
                for(String idx : h.split("\\|")){
                    hiid.add(Long.parseLong(idx));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
        return hiid;
    }

}
