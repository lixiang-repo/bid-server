FROM registry.cn-hangzhou.aliyuncs.com/lixiang666/bid-server:latest

# 项目名称, 请自行替换
ENV APP_NAME=bid-server

# 根据项目修改打包
COPY target/bid-server.jar /data/html/${APP_NAME}.jar

# java 启动参数, 请谨慎变更
CMD java -server -Xmx5g -Xms5g -Xmn1048M -XX:MetaspaceSize=512m -XX:MaxMetaspaceSize=4096m -XX:+PrintGC -XX:+PrintGCDetails \
    -XX:+UnlockDiagnosticVMOptions -XX:NativeMemoryTracking=detail -XX:InitiatingHeapOccupancyPercent=35 -XX:MinMetaspaceFreeRatio=50 -XX:MaxMetaspaceFreeRatio=80 \
    -XX:+PrintTenuringDistribution -XX:+PrintGCDateStamps -XX:+ExplicitGCInvokesConcurrentAndUnloadsClasses -XX:CMSInitiatingOccupancyFraction=70 \
    -XX:+UseCMSInitiatingOccupancyOnly -Xloggc:/data/logs/${POD_NAME}-gc-%p-%t.log -XX:+UseParNewGC -XX:+UseConcMarkSweepGC -XX:+PrintHeapAtGC \
    -XX:+PrintGCApplicationConcurrentTime -XX:+PrintGCApplicationStoppedTime -XX:PrintFLSStatistics=1 -XX:-OmitStackTraceInFastThrow \
    -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/data/logs/${POD_NAME}-oom-%p-%t.hprof \
    -jar /data/html/${APP_NAME}.jar -Djava.security.egd=file:/dev/urandom \
    --server.port=8080 --spring.profiles.active=prod --cli --server.address=0.0.0.0
