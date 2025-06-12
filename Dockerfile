FROM eclipse-temurin:21-jre

RUN mkdir -p /opt/cprof && \
  apt-get update && apt-get install -y curl && \
  curl https://storage.googleapis.com/cloud-profiler/java/latest/profiler_java_agent.tar.gz \
  | tar xzv -C /opt/cprof && \
  rm -rf /var/lib/apt/lists/*

RUN useradd -ms /bin/bash lupapiste
USER lupapiste
RUN mkdir -m 700 /home/lupapiste/.ssh && install -m 600 /dev/null /home/lupapiste/.ssh/known_hosts
COPY target/lupapalvelu-0.1.0-SNAPSHOT-standalone.jar /home/lupapiste/app.jar
ADD lupapiste.properties /home/lupapiste/lupapiste.properties
ADD ./entrypoint.sh /home/lupapiste/entrypoint.sh
ENV LUPAPISTE_MODE="dev"
ENV SYS_OPTS="-XX:+UseG1GC -Dlogback.configurationFile=/home/lupapiste/logback.xml -Djava.util.prefs.systemRoot=/home/lupapiste/.java \
-Djava.util.prefs.userRoot=/home/lupapiste/.java/.userPrefs -Dlupapiste.loglevel=debug -Dcom.sun.security.enableAIAcaIssuers=true \
-agentpath:/opt/cprof/profiler_java_agent.so=-cprof_service=lupapiste,-cprof_service_version=1.256,-cprof_cpu_use_per_thread_timers=true,-cprof_enable_heap_sampling=true"
ENV JAR_OPTS="lupapalvelu.main server"
ENV JAR_CMD="-cp ./app.jar"

WORKDIR /home/lupapiste

EXPOSE 8000

ENTRYPOINT ./entrypoint.sh
