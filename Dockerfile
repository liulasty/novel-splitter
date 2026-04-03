# 第一阶段：Maven 构建（使用 BuildKit 缓存挂载，实现极速构建）
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app

# 复制项目代码
COPY . .

# 构建（利用 Docker BuildKit 缓存挂载 /root/.m2 目录，极大加速后续构建）
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -e -B

# 第二阶段：最小运行时镜像
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# 创建非 root 用户运行（安全最佳实践）
RUN useradd -m -u 1000 appuser && \
    chown -R appuser:appuser /app
USER appuser

# 从构建阶段复制 jar
COPY --from=builder /app/application/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:--Xmx1g -Xms512m} -jar app.jar"]