# 第一阶段：复制本地构建产物
# 要求：构建镜像前先执行 mvn clean package -DskipTests
FROM eclipse-temurin:21-jre-jammy AS builder
WORKDIR /app

# 复制本地已构建好的 Spring Boot fat jar（由 interfaces 模块的 spring-boot-maven-plugin 打出）
COPY interfaces/target/interfaces-*.jar app.jar

# 提取 Spring Boot 分层
RUN java -Djarmode=layertools -jar app.jar extract

# 第二阶段：最小运行时镜像
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# 创建非 root 用户运行
RUN useradd -m -u 1000 appuser && \
    chown -R appuser:appuser /app
USER appuser

# 从构建阶段复制分层文件
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:--Xmx1g -Xms512m} org.springframework.boot.loader.launch.JarLauncher"]
