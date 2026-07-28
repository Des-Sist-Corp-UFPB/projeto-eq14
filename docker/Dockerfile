# =============================================================================
# Dockerfile de PRODUÇÃO — Multi-stage build
# =============================================================================
# Estágio 1 (builder): compila o projeto com Maven e gera o JAR.
# Estágio 2 (runtime): imagem mínima com apenas o JRE, sem ferramentas de build.
#
# Vantagens do multi-stage:
#   - Imagem final muito menor (sem Maven, sem fontes Java, sem classes temp.)
#   - Superfície de ataque reduzida (menos binários = menos vulnerabilidades)
#   - Separação entre ambiente de build e runtime
#
# Como construir:
#   docker build -f docker/Dockerfile -t mercado:latest .
# =============================================================================

# ---- Estágio 1: Build -------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Agente OpenTelemetry (SPEC-14) — baixado no estágio de build (a imagem maven tem curl;
# o runtime -jre não precisa ganhar ferramenta de rede) com versão PINADA (Art. I).
# curl -fsSL falha o build em erro HTTP em vez de gerar um .jar corrompido, mantendo o
# gate do CI honesto (Art. XII). O agente vai para o runtime abaixo, mas fica INERTE até
# JAVA_TOOL_OPTIONS referenciá-lo (liga/desliga sem rebuild).
ARG OTEL_AGENT_VERSION=2.30.0
RUN curl -fsSL -o /opentelemetry-javaagent.jar \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar"

# Copiar pom.xml primeiro para cache de dependências
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# Copiar código-fonte e compilar
COPY src ./src
RUN mvn clean package -DskipTests -B -q

# ---- Estágio 2: Runtime -----------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

# Criar usuário não-root por segurança
# Executar aplicações como root em container é má prática de segurança
RUN groupadd --gid 1001 mercado && \
    useradd --uid 1001 --gid mercado --shell /bin/false mercado

WORKDIR /app

# Copiar apenas o JAR do estágio anterior
COPY --from=builder /build/target/*.jar app.jar

# Agente OpenTelemetry (SPEC-14): presente na imagem desde o build, porém INERTE — só é
# anexado à JVM se JAVA_TOOL_OPTIONS=-javaagent:/app/opentelemetry-javaagent.jar for
# definido (no .env do servidor). Sem isso, a JVM nem o carrega: comportamento idêntico
# ao atual (RN-OBS-01). O ENTRYPOINT abaixo NÃO muda.
COPY --from=builder /opentelemetry-javaagent.jar opentelemetry-javaagent.jar

# Definir owner dos arquivos (app + agente)
RUN chown mercado:mercado app.jar opentelemetry-javaagent.jar

# Trocar para usuário não-root
USER mercado

EXPOSE 8080

# Health check: o Actuator expõe /actuator/health
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Opções JVM para produção:
#   -XX:+UseContainerSupport: respeita limites de memória do container
#   -XX:MaxRAMPercentage=75: usa até 75% da RAM disponível para o heap
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Dspring.profiles.active=prod", \
    "-jar", "app.jar"]
