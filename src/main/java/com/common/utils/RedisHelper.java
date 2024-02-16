package com.common.utils;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;


public class RedisHelper {

    private static final Logger logger = LoggerFactory.getLogger(RedisHelper.class);
    public static String redisHosts = "";
    private static Properties properties = new Properties();
    static {
        try {
            properties.load(new InputStreamReader(RedisHelper.class.getClassLoader().getResourceAsStream("application.properties"), "UTF-8"));
            String profile = properties.getProperty("spring.profiles.active");
            String envFile = "application-" + profile + ".properties";
            Properties envProps = new Properties();
            envProps.load(new InputStreamReader(RedisHelper.class.getClassLoader().getResourceAsStream(envFile), "UTF-8"));
            redisHosts = envProps.getProperty("redis.hosts");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static ShardedJedisPool pool = null;
    static {
        String[] hostAndPorts = redisHosts.split(",");
        List<JedisShardInfo> jedisShardInfoList = new ArrayList<>(hostAndPorts.length);
        for(String hostAndPort : hostAndPorts) {
            String host = hostAndPort.split(":")[0];
            int port = Integer.parseInt(hostAndPort.split(":")[1]);
            JedisShardInfo jedisShardInfo = new JedisShardInfo(host, port, 10000);
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

    public static ArrayList<String> getList(String prefix, String key) {
        ArrayList<String> list = new ArrayList<>();
        ShardedJedis jedis = null;
        try {
            jedis = pool.getResource();
            String value = jedis.get(prefix + key);
            if(value != null) {
                list.addAll(Arrays.asList(value.split(",")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
        return list;
    }

}
