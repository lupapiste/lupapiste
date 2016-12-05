#!/bin/bash

## robot running script for Linux

MAXTHREADS=1
SCREEN=100
OUTPUT=xvfb
RESOLUTION=1200x1200
TIMEOUT=360
TESTS=
NOENV=
SERVER=http://localhost:8000
LUPISPID=
NICENESS=+15
STARTUPTIMEOUT=60


# kill recursive all leaf processes and the given one
recursive_kill() { # sig pid indent
   SUBS=$(pgrep -P $2)
   echo "${3}Killing $2 ($SUBS)"
   for SUB in $SUBS; do
      recursive_kill $1 $SUB "  $3"
   done
   echo "${3}Closing $2"
   kill $1 $2
}

fail() {
   echo "Roboto fail: $@"
   exit 1
}

usage() {
   echo \
"roboto.sh [args] test-dir ...
  -j | --threads n  use up to n threads, one per argument test
  -n | --nested     use a nested X server instead of a virtual one
  -l | --local      use current X server
  -s | --start      start lupapiste with lein
  -t | --timeout    timeout for individual robot file [$TIMEOUT]
  -h | --help       show this thing
  -S | --server     use a specific server [$SERVER]
"
}

# start lupapiste, assuming we're runnign at robot/
start_lupapiste() {
   lupapiste_runningp && fail "Trying to start lupapiste, but it's already running at $SERVER"
   cd ..
   test -d src || fail "Cannot start lupapiste at $(pwd), can't see src/ here"
   echo "Starting lupapiste."
   lein run &> lupapiste-roboto.log &
   LUPISPID=$!
   echo -n "Waiting for lupapiste $LUPISPID: "
   for foo in $(seq $STARTUPTIMEOUT)
   do
      grep -q 'You can view the site at http://localhost:8000' lupapiste-roboto.log && break
      echo -n "x"
      sleep 1
   done
   echo
   cd robot
   lupapiste_runningp || fail "Failed to bring up Lupapiste. Check lupapiste-roboto.log for details."
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
      (-l|--local)
         OUTPUT=local
         shift
         parse_args $@
         ;;
      (-E|--no-env-check)
         NOENV=1
         parse_args $@
         ;;
      (-S|--server)
         SERVER=$2
         shift 2
         parse_args $@
         ;;
      (-s|--start)
         start_lupapiste
         shift
         parse_args $@
         ;;
      (-t|--timeout)
         TIMEOUT=$2
         shift 2
         echo "$TIMEOUT" | grep -q "^[0-9]*$" || fail "bad timeout"
         parse_args $@
         ;;
      (*)
         TESTS=$@
         echo "Tests are $TESTS"
         ;;
   esac
}

lupapiste_runningp() {
   curl -s "$SERVER/api/alive" | grep -q unauthorized || return 1
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
   lupapiste_runningp || fail "A web server does not appear to be running at '$SERVER'"
}


parse_args $@

check_env

echo "Starting robots"

run_test() {
   test=$2
   TEST=$(echo $test | sed -e 's/[/ ]/_/g')
   case $OUTPUT in
      xvfb)
         SCREEN=$1
         echo "STARTING Xvfb :$SCREEN -screen 0 ${RESOLUTION}x24 -pixdepths 16,24"
         Xvfb :$SCREEN -screen 0 ${RESOLUTION}x24 -pixdepths 16,24 2>&1 &
         XPID=$!
         DISPLAY=:$SCREEN
         echo "DISPLAY set to $DISPLAY"
         ;;
      xnest)
         SCREEN=$1
         Xnest :$SCREEN -geometry ${RESOLUTION}+0+0 &>/dev/null &
         DISPLAY=:$SCREEN
         XPID=$!
         ;;
      local)
         # use current DISPLAY
         SCREEN=$DISPLAY
         XPID=""
         ;;
      *)
         fail "output must be xvfb or xnest"
         ;;
   esac
  
   sleep 2 # wait for X to start
   
   WMPID=""
   # start openbox to handle window maximize if we're running in a non-local X
   test -z "$XPID" || { 
      DISPLAY=:$SCREEN openbox &>/dev/null &
      WMPID=$! 
   }
  
   # disable screensaver (needed when recording) 
   test -z "$XPID" || DISPLAY=:$SCREEN xset s off
   
   sleep 2 # wait for window manager to start
   
   mkdir -p target # make log directory if necessary
 
   # exclude tests with non-roboto-proof tag
   DISPLAY=:$SCREEN timeout $TIMEOUT pybot \
      --exclude integration \
      --exclude ajanvaraus \
      --exclude fail \
      --exclude non-roboto-proof \
      --RunEmptySuite \
      --variable SERVER:$SERVER \
      -d target \
      -L TRACE \
      -b $TEST.debug.log \
      -o $TEST.xml \
      -l $TEST.log \
      -r $TEST.html \
         common/setup "$test" common/teardown &> target/$TEST.out

   BOT=$?
   test -z "$WMPID" || kill -9 $WMPID; sleep 1
   test -z "$XPID" || kill -9 $XPID; sleep 1
   test "$BOT" = "0" || { 
      echo "ERROR: pybot run exited with $BOT for test '$test'"; 
      echo "FAIL: pybot exited with non-zero $BOT, timeout was $TIMEOUT" >> target/$TEST.out;
   }
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
   ls target | grep -q "\.out" || { echo "No results yet."; return; }
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

# clear results
test -d target && rm -rf target
mkdir target

# lower priority
renice $NICENESS $$

# run tests
echo "Running tests $TESTS"
for test in $(find $TESTS | grep "\/[0-9][^/]*\.robot$" | sort -r)
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

halt() {
   echo "Cleaning up..."
   test -n "$WMPID" && { echo "Closing WM $WMPID"; kill -9 $WMPID &>/dev/null; }
   sleep 1
   test -n "$XPID" && { echo "Closing X $XPID"; kill -9 $XPID &>/dev/null; }
   sleep 1
   test -z "$LUPISPID" || { 
      echo "Shutting down lupapiste $LUPISPID"
      # pstree -p $LUPISPID
      recursive_kill -9 $LUPISPID ""
      for foo in $(seq 10)
      do
         sleep 1
         lupapiste_runningp || break
      done
      lupapiste_runningp && fail "Failed to shut down lupapiste at end of test run"
   }
}

halt

echo "Tests finished."
