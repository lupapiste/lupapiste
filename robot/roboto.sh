#!/bin/bash

## robot running script for Linux

MAXTHREADS=1
SCREEN=100
OUTPUT=xvfb
RESOLUTION=1400x1200
TIMEOUT=600
TESTS=
NOENV=
SERVER=http://localhost:8000
LUPISPID=
NICENESS=+15
STARTUPTIMEOUT=60
PERFECT=
BLACKLIST=
RETRIES=3
UPDATE=15

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
  -j | --threads n      use up to n threads, one per argument test [$MAXTHREADS]
  -n | --nested         use a nested X server instead of a virtual one (Xvfb)
  -l | --local          use current X server ($DISPLAY)
  -s | --start          start lupapiste with lein
  -t | --timeout n      timeout for individual robot file [$TIMEOUT]
  -h | --help           show this thing
  -S | --server uri     use a specific server [$SERVER]
  -p | --perfect        fail immediately if anything fails
  -b | --blacklist path skip tests in roboto-blacklist.txt
  -r | --retries n      maximum number of failing suite reruns [$RETRIES]
"
}

# start lupapiste, assuming we're runnign at robot/
start_lupapiste() {
   lupapiste_runningp && fail "Trying to start lupapiste, but it's already running at $SERVER"
   cd ..
   test -d src || fail "Cannot start lupapiste at $(pwd), can't see src/ here"
   echo "Starting lupapiste."
   lein run &> robot/target/lupapiste.log &
   LUPISPID=$!
   echo -n "Waiting for lupapiste $LUPISPID: "
   for foo in $(seq $STARTUPTIMEOUT)
   do
      grep -q 'You can view the site at http://localhost:8000' robot/target/lupapiste.log && break
      echo -n "x"
      sleep 1
   done
   echo
   cd robot
   lupapiste_runningp || fail "Failed to bring up Lupapiste. Check robot/target/lupapiste.log for details."
}

check_integer() {
   echo "$1" | grep -q "^[0-9][0-9]*$" || fail "Error: $2: '$1'"
}

check_nonzero_integer() {
   echo "$1" | grep -q "^[1-9][0-9]*$" || fail "Error: $2: '$1'"
}

parse_args() {
   if [ 0 = $# ]; then
      fail "No tests given"
   fi
   ARG=$1
   case $ARG in
      (-j|--threads)
         MAXTHREADS=$2
         check_integer "$MAXTHREADS" "Number of threads should be an integer"
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
         check_nonzero_integer "$TIMEOUT" "Bad timeout"
         parse_args $@
         ;;
      (-p|--perfect)
         PERFECT=1
         shift
         echo "Failure is not an option"
         parse_args $@
         ;;
      (-b|--blacklist)
         BLACKLIST=$2
         echo "Skipping tests in '$BLACKLIST'"
         test -f $BLACKLIST || fail "$BLACKLIST is not a file"
         shift 2
         parse_args $@
         ;;
      (-r|--retries)
         RERUNS=$2
         check_integer "$RERUNS" "Reruns must be an integer"
         shift 2
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
   pgrep -u $(whoami) Xvfb && fail "There are Xvfbs running for me already"
}


parse_args $@

check_env

echo "Starting robots"

run_test() {
   local test=$2
   local TEST=$(echo $test | sed -e 's/[/ ]/_/g')
   local BOT=
   local MYSCREEN=$1
   case $OUTPUT in
      xvfb)
         MYSCREEN=$1
         Xvfb :$MYSCREEN -screen 0 ${RESOLUTION}x24 -pixdepths 16,24 2>&1 &
         XPID=$!
         DISPLAY=:$MYSCREEN
         ;;
      xnest)
         MYSCREEN=$1
         Xnest :$MYSCREEN -geometry ${RESOLUTION}+0+0 &>/dev/null &
         DISPLAY=:$MYSCREEN
         XPID=$!
         ;;
      local)
         # use current DISPLAY
         MYSCREEN=$DISPLAY
         XPID=""
         ;;
      *)
         fail "output must be xvfb or xnest"
         ;;
   esac

   # Wait for X to be ready for connections
   for foo in $(seq 20)
   do
     DISPLAY=:$MYSCREEN xwininfo -root 2>&1 | grep -q "root window" && break
     sleep 1
   done
   DISPLAY=:$MYSCREEN xwininfo -root | grep -q "root window" || fail "FAIL: failed to bring up X at :$MYSCREEN for test $test"

   local WMPID=""
   # start openbox to handle window maximize if we're running in a non-local X
   test -z "$XPID" || {
      DISPLAY=:$MYSCREEN openbox &>/dev/null &
      WMPID=$!
   }

   sleep 2 # wait for window manager to start

   mkdir -p target # make log directory if necessary

   # -L TRACE
   for ROUND in $(seq 0 $RETRIES)
   do
      DISPLAY=:$MYSCREEN timeout $TIMEOUT pybot \
         --exclude integration \
         --exclude ajanvaraus \
         --exclude fail \
         --exclude non-roboto-proof \
         --RunEmptySuite \
         --variable SERVER:$SERVER \
         -d target \
         --exitonfailure \
         -b $TEST.debug.log \
         -o $TEST.xml \
         -l $TEST.log.html \
         -r NONE \
            common/setup "$test" common/teardown &> target/$TEST.out
      BOT=$?
      test "0" "=" "$BOT" && break
      # sometimes ff seems to have persistent trouble starting and/or selenium connecting to it
      # ruling out causes by by spreading pybot startups temporally and seeing if the issue persists
      sleep 5
   done
   # round 0 is first
   ROUND=$(expr $ROUND "+" 1)
   LASTROUND=$(expr $RETRIES "+" 1)
   echo "NOTE: pybot exited with $BOT after round $ROUND/$LASTROUND" >> target/$TEST.out;
   # shut down X and WM if they were started
   test -z "$WMPID" || { kill -9 $WMPID; wait $WMPID; } &>/dev/null; sleep 1
   test -z "$XPID" || { kill -9 $XPID; wait $XPID; } &>/dev/null; sleep 1
}

RED='\033[0;31m'
LIGHTRED='\033[1;31m'
DEFAULT='\033[0m'
BRIGHT='\033[1m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'

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
   echo "Cleaning up /tmp/*/webdriver-py-profilecopy"
   rm -rf /tmp/*/webdriver-py-profilecopy
   echo "Writing report.html"
   rebot --outputdir target --report report.html --name Roboto target/*.xml
}

maybe_finish() {
   test "$1" -gt 0 && test -n "$PERFECT" && {
      echo "Not tolerating a failure."
      recursive_kill -9 $$ ""
      # this won't be reached
      fail "Stopping due to failure in perfect mode"
   }
   test -f "stop" && {
      echo "./stop file exists. Stopping robots."
      rm stop
      recursive_kill -9 $$ ""
      # won't be reached
      exit 0
   }
}

show_finished() {
   local STATUS=$(grep "tests total" $1 | tail -n 1)
   local COLOR=$GREEN
   local ATTEMPTS=$(grep "pybot exited with" "$1" | sed -e 's/.*round //')
   echo "$STATUS" | grep -q "0 failed" || COLOR=$RED
   echo -e "$COLOR - $1 done: $STATUS, $ATTEMPTS runs"
   grep -E "FAIL" "$1" | head -n 1 | sed -e 's/^/      /'
   echo -n -e "$DEFAULT"
}

show_running() {
  local NPASS=$(grep PASS $log | wc -l)
  local NFAIL=$(grep FAIL $log | wc -l)
  local COLOR=$LIGHTRED
  test "$NFAIL" -lt "$NPASS" && COLOR=$RED
  test "$(expr $NFAIL '*' 5)" -lt "$NPASS" && COLOR=$YELLOW
  test "$NFAIL" = 0 && COLOR=$GREEN
  echo -e "$COLOR o $log $(grep FAIL $log | wc -l) failed, $(grep PASS $log | wc -l) ok"
  grep -E "(FAIL|exited with)" "$1" | head -n 1 | sed -e 's/^/      /'
  echo -e -n "$DEFAULT"
  maybe_finish $NFAIL
}

show_stats() {
   echo "---------------------- 8< ----------------------"
   MSG="$@"
   NOW=$(date +'%H:%M:%S %d.%m.%Y')
   test -z "$MSG" && MSG="Waiting for $(jobs | grep run_test | wc -l) threads"
   echo -e "${BRIGHT}$NOW: ${MSG}${DEFAULT}"
   ls target | grep -q "\.out" || { echo "No results yet."; return; }
   for log in target/*.out
   do
      if [ -z "$(grep -E '(pybot exited|^Report: )' $log)" ]
      then
         show_running $log
      else
         show_finished $log
      fi
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
      sleep $UPDATE
      jobs > /dev/null
   done
   test -n "$BLACKLIST" && grep -q "$test" "$BLACKLIST" && {
      echo "WARNING: skippin blacklisted test $test";
      continue; }
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
   sleep $UPDATE
   jobs > /dev/null
done

show_stats

halt

echo "Tests finished."
