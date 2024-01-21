FROM registry.cn-hangzhou.aliyuncs.com/lixiang666/bid-server:latest

# 根据项目修改打包
COPY ./bid-server.jar /data/bid-server.jar
COPY ./entrypoint.sh /data/entrypoint.sh

RUN chmod +x /data/entrypoint.sh

ENTRYPOINT ["/data/entrypoint.sh"]