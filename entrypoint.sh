#!/bin/bash

# Exit when any command fails
set -eu

# Copy env specific config.edn in use, mounted from kubernetes secret to pod
yes | cp -f /etc/config/* /home/lupapiste

exec java -server -Dio.netty.native.workdir=/var/lib/netty -Dmail.host=lupapiste.fi -XX:MaxRAMPercentage=75 -XX:+UnlockExperimentalVMOptions -Dlupapiste.mode=$LUPAPISTE_MODE $SYS_OPTS $JAR_CMD $JAR_OPTS
