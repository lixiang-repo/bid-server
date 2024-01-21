#!/usr/bin/env bash

# java 启动参数, 请谨慎变更
java -server -Xmx5g -Xms5g -Xmn1048M -XX:MetaspaceSize=512m -XX:MaxMetaspaceSize=4096m -XX:+PrintGC -XX:+PrintGCDetails \
  -XX:+UnlockDiagnosticVMOptions -XX:NativeMemoryTracking=detail -XX:InitiatingHeapOccupancyPercent=35 -XX:MinMetaspaceFreeRatio=50 -XX:MaxMetaspaceFreeRatio=80 \
  -XX:+PrintTenuringDistribution -XX:+PrintGCDateStamps -XX:+ExplicitGCInvokesConcurrentAndUnloadsClasses -XX:CMSInitiatingOccupancyFraction=70 \
  -XX:+UseCMSInitiatingOccupancyOnly -Xloggc:/data/logs/bid-server-gc-%p-%t.log -XX:+UseParNewGC -XX:+UseConcMarkSweepGC -XX:+PrintHeapAtGC \
  -XX:+PrintGCApplicationConcurrentTime -XX:+PrintGCApplicationStoppedTime -XX:PrintFLSStatistics=1 -XX:-OmitStackTraceInFastThrow \
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/data/logs/bid-server-oom-%p-%t.hprof \
  -jar /data/bid-server.jar -Djava.security.egd=file:/dev/urandom \
  --server.port=8080 --spring.profiles.active=prod --cli --server.address=0.0.0.0