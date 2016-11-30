#!/bin/bash

## robot running script for Linux

# todo: check root window minimum geometry with xwininfo -root
# todo: speed check (e.g. dd + urandom + gzip)
# todo: flaky tags
# todo: check for openbox and needed X servers, if used
# todo: option to run in local X missing

MAXTHREADS=1
SCREEN=100
OUTPUT=xvfb
RESOLUTION=1200x1200
TESTS=
NOENV=
SERVER=http://localhost:8000

usage() {
   echo \
"roboto.sh [args] test-dir ...
  -j | --threads n  use up to n threads, one per argument test
  -n | --nested     use a nested X server instead of a virtual one
  -s | --server     use a specific server [$SERVER]
  -h | --help       show this thing
"
}

parse_args() {
   if [ 0 = $# ]; then
      fail "No tests given"
   fi
   ARG=$1
   case $ARG in
      (-j|--threads)
         MAXTHREADS=$2
         shift 2
         echo "Using up to $MAXTHREADS threads."
         parse_args $@
         ;;
      (-n|--nested)
         OUTPUT=xnest
         echo "Using Xnest"
         shift
         parse_args $@
         ;;
      (-h|--help)
         usage
         exit 0
         ;;
      (-E|--no-env-check)
         NOENV=1
         ;;
      (-s|--server)
         SERVER=$2
         shift 2
         parse_args $@
         ;;
      (*)
         TESTS=$@
         echo "Tests are $TESTS"
         ;;
   esac
}

fail() {
   echo "Roboto fail: $@"
   exit 1
}

check_env() {
   test -z "$NOENV" || return
   # routes
   echo -n "Checkign routes: "
   /sbin/route -n | grep -q "^172.17.*docker0" && fail "Docker is using axe.lupapiste.fi IP route." || echo ok
   # connectivity
   #echo -n "Checking axe: "
   #lynx -dump http://172.17.125.102:8080 | grep -q Jenkins && echo OK || fail "No Jenkins at http://172.17.125.102:8080. You may have forgotten VPN or need also sudo route add 172.17.125.102 dev tun0."
   # selenium version
   echo -n "Checking selenium version: "
   SELENIUM=$(pip list | grep "^selenium ")
   echo "$SELENIUM" | grep "selenium (2\.53\.[0-9]*)" || fail "Your selenium version '$SELENIUM' may cause tests to fail. Update script or change to 2.53.*."
   # (lack of) geckodriver
   echo "Browser: "
   which geckodriver && fail "Remove geckodriver from path."
   FF=$(firefox --version)
   echo "$FF" | grep "^Mozilla Firefox 45\." || fail "Major version '$FF' of Firefox may not work yet. Update script if it's ok."   
   echo -n "Server: $SERVER "
   curl -s "$SERVER/system/ping" | grep -q true && echo "ok" || fail "A web server does not appear to be running at '$SERVER'"
}

parse_args $@

check_env

echo "Starting robots"

# each pybot runs in a fresh X with only one browser window
run_test() {
   SCREEN=$1
   test=$2
   TEST=$(echo $test | sed -e 's/[/ ]/_/g')
   #echo "Starting test $TEST on screen $SCREEN"
   case $OUTPUT in
      xvfb)
         Xvfb :$SCREEN -screen 0 ${RESOLUTION}x24 -pixdepths 16,24 2>&1 &
         XPID=$!
         ;;
      xnest)
         Xnest :$SCREEN -geometry ${RESOLUTION}+0+0 &>/dev/null &
         XPID=$!
         ;;
      *)
         fail "output must be xvfb or xnest"
         ;;
   esac
   sleep 1
   # browser window maximize works in openbox, but not in many other small wms
   DISPLAY=:$SCREEN openbox &>/dev/null & 
   DISPLAY=:$SCREEN xset s off # disable screensaver
   WMPID=$!
   sleep 1
   DISPLAY=:$SCREEN pybot \
      --exclude integration \
      --exclude ajanvaraus \
      --exclude fail \
      --exclude non-parallel \
      --RunEmptySuite \
      --variable SERVER:$SERVER \
      -d target \
      -o $TEST.xml \
      -l $TEST.log \
      -r $TEST.html \
         common/setup "$test" common/teardown &> target/$TEST.out
   kill -9 $WMPID
   sleep 1
   kill -9 $XPID
}

RED='\033[0;31m'
LIGHTRED='\033[1;31m'
DEFAULT='\033[0m'
BRIGHT='\033[1m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'

show_stats() {
   echo "---------------------- 8< ----------------------"
   MSG="$@"
   NOW=$(date +'%H:%M:%S %d.%m.%Y')
   test -z "$MSG" && MSG="Waiting for $(jobs | grep run_test | wc -l) threads"
   echo -e "${BRIGHT}$NOW: ${MSG}${DEFAULT}"
   for log in target/*.out
   do
      NPASS=$(grep PASS $log | wc -l)
      NFAIL=$(grep FAIL $log | wc -l)
      COLOR=$LIGHTRED
      test "$NFAIL" -lt "$NPASS" && COLOR=$RED
      test "$(expr $NFAIL '*' 5)" -lt "$NPASS" && COLOR=$YELLOW
      test "$NFAIL" = 0 && COLOR=$GREEN
      STATUS="$COLOR - $log $(grep FAIL $log | wc -l) failed, $(grep PASS $log | wc -l) ok$DEFAULT"
      echo -e $STATUS
   done
}

rm target/*.out
echo "Running tests $TESTS"
for test in $TESTS
do
   RUNNING=$(jobs | grep run_test | wc -l)
   while [ $RUNNING -ge $MAXTHREADS ]
   do
      RUNNING=$(jobs | grep run_test | wc -l)
      show_stats "$RUNNING/$MAXTHREADS threads running"
      sleep 10
      jobs > /dev/null
   done 
   TEST=$(echo $test | sed -e 's/[/ ]/_/g')
   echo " - Starting $test"
   run_test $SCREEN "$test" &
   SCREEN=$(expr $SCREEN + 1)
   sleep 1
done

# wait for last threads to finish
while true
do
   jobs | grep -q run_test || break
   show_stats
   sleep 10
   jobs > /dev/null
done 

echo "Tests finished"
echo "Closing WM $WMPID"
kill -9 $WMPID &>/dev/null
sleep 1
echo "Closing X $XPID"
kill -9 $XPID &>/dev/null

