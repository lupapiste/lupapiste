#!/bin/bash

## robot running script for Linux

MAXTHREADS=1
SCREEN=100
OUTPUT=xvfb
RESOLUTION=1400x1200
TIMEOUT=600
TESTS=
NOENV=
BROWSER=firefox
SERVER=http://localhost:8000
LUPISPID=
NICENESS=+15
STARTUPTIMEOUT=300 
PERFECT=
BLACKLIST=
RETRIES=3
UPDATE=15
EXCLUDES="--exclude fail --exclude non-roboto-proof"
INCLUDE_TAGS=
INCLUDES=
PARALLEL=files
LOWMEMMB=512
MAXCOREUNDERFLOW=3
ORDER=modified

RED='\033[0;31m'
LIGHTRED='\033[1;31m'
DEFAULT='\033[0m'
BRIGHT='\033[1m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'

# kill recursive all leaf processes and the given one
recursive_kill() { # sig pid indent
   local SUBS=$(pgrep -P $2)
   echo "${3}Killing $2 ($SUBS)"
   for SUB in $SUBS; do
      recursive_kill $1 $SUB "  $3"
   done
   echo "${3}Closing $2"
   kill $1 $2
}

load_warning() {
   # 1 min fully loaded cores
   local LOADED=$(cat /proc/loadavg | sed -e 's/\..*//')
   # available cores
   local CORES=$(grep bogomips /proc/cpuinfo | wc -l)
   test "$LOADED" -gt $(expr "$CORES" "+" "$MAXCOREUNDERFLOW") \
      && echo -e "${YELLOW}Warning: heavy load. $LOADED/$CORES cores at work.$DEFAULT"
}

mem_warning() {
   local AVAILKB=$(grep MemAvailable /proc/meminfo | sed -e 's/[^0-9]//g')
   local AVAILMB=$(expr "$AVAILKB" "/" "1024")
   test "$AVAILMB" -gt $LOWMEMMB \
      || echo -e "${YELLOW}Warning: only ${AVAILMB}MB memory free$DEFAULT"
}

load_warnings() {
   mem_warning
   load_warning
}

fail() {
   echo -e "${RED}Roboto fail:$YELLOW $@$NORMAL"
   exit 1
}

n_tests() {
   jobs | grep run_test | grep Running | wc -l
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
  -B | --browser name   'firefox' (default) or 'chrome'
  -S | --server uri     use a specific server [$SERVER]
  -p | --perfect        fail immediately if anything fails
  -b | --blacklist path skip tests in roboto-blacklist.txt
  -r | --retries n      maximum number of failing suite reruns [$RETRIES]
  -e | --exclude tags   comma separated list of tags to exclude;
                        fail and non-roboto-proof are always excluded
  -i | --include tags   comma separated list of tags to include
  -E | --no-env-check   skip version and environment startup sanity checks
  -P | --parallel style 'files': find files under test-dirs and run them in 
                                 parallel (default)  
                        'args':  run test-dirs in parallel
  -o | --order          testsuite order (modified,numeric,random) [$ORDER]
"
}

# start lupapiste, assuming we're runnign at robot/
start_lupapiste() {
   lupapiste_runningp && fail "Trying to start lupapiste, but it's already running at $SERVER"
   cd ..
   test -d src || fail "Cannot start lupapiste at $(pwd), can't see src/ here"
   echo "Starting lupapiste."
   mkdir -p robot/target
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
         shift
         parse_args $@
         ;;
      (-B|--browser)
         BROWSER=$2
         shift 2
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
         RETRIES=$2
         check_integer "$RETRIES" "Reruns must be an integer"
         shift 2
         parse_args $@
         ;;
      (-e|--exclude)
         local tags=$(echo $2 | tr "," "\n")
         for tag in $tags; do
            EXCLUDES="$EXCLUDES --exclude $tag"
         done
         shift 2
         parse_args $@
         ;;
      (-i|--include)
         INCLUDE_TAGS=$(echo $2 | tr "," "\n")
         for tag in $INCLUDE_TAGS; do
            INCLUDES="$INCLUDES --include $tag"
         done
         shift 2
         parse_args $@
         ;;
      (-P | --parallel)
         PARALLEL=$2
         shift 2
         parse_args $@
         ;;
      (-o | --order)
         ORDER=$2
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

versionfail() {
   fail "$@ Version tests can be skipped with -E flag."
}

check_env() {
   test -z "$NOENV" || return
   # routes
   #echo -n "Checkign routes: "
   #/sbin/route -n | grep -q "^172.17.*docker0" && fail "Docker is using axe.lupapiste.fi IP route." || echo ok
   # connectivity
   #echo -n "Checking axe: "
   #lynx -dump http://172.17.125.102:8080 | grep -q Jenkins && echo OK || fail "No Jenkins at http://172.17.125.102:8080. You may have forgotten VPN or need also sudo route add 172.17.125.102 dev tun0."
   # selenium version
   echo -n "Checking selenium version: "
   SELENIUM=$(pip list | grep "^selenium ")
   echo "$SELENIUM" | grep "selenium (2\.53\.[0-9]*)" || versionfail "Your selenium version '$SELENIUM' may cause tests to fail. Update ${BASH_SOURCE}:${LINENO} to match the current selenium version if it is fine, or change to 2.53.*."

   echo "Browser: $BROWSER"
   case "$BROWSER" in
      "firefox" )
         # (lack of) geckodriver
         which geckodriver 2> /dev/null && fail "Remove geckodriver from path."
         FF=$(firefox --version)
         echo "$FF" | grep -q "^Mozilla Firefox 45\." || versionfail "Major version '$FF' of Firefox may not work yet. Update ${BASH_SOURCE}:${LINENO} if this is fine."
         ;;
      "chrome" )
         CG=$(google-chrome --version)
         echo "$CG" | grep -q "^Google Chrome 55\." || versionfail "Major version '$CG' of Chrome may not work yet. Update ${BASH_SOURCE}:${LINENO} if this is fine."
         ;;
      * )
         fail "Unsupported browser '$BROWSER'"
         ;;
   esac

   echo "Server: $SERVER"
   lupapiste_runningp || fail "A web server does not appear to be running at '$SERVER'"
   pgrep -u $(whoami) Xvfb && fail "There are Xvfbs running for $(whoami) already"
}

test -d target && rm -rf target
mkdir target

parse_args $@

check_env

echo "Starting robots"

run_test() {
   local test=$2
   local TEST=$(echo $test | sed -e 's/[/ ]/_/g')
   local BOT=
   local MYSCREEN=$1
   local DISPLAY=$DISPLAY
   case $OUTPUT in
      xvfb)
         MYSCREEN=$1
         Xvfb :$MYSCREEN -dpi 75 -screen 0 ${RESOLUTION}x24 -pixdepths 16,24 2>&1 &
         XPID=$!
         DISPLAY=:$MYSCREEN
         ;;
      xnest)
         MYSCREEN=$1
         Xnest :$MYSCREEN -dpi 75 -geometry ${RESOLUTION}+0+0 &>/dev/null &
         DISPLAY=:$MYSCREEN
         XPID=$!
         ;;
      local)
         # use current DISPLAY
         MYSCREEN=$(echo $DISPLAY | sed -e 's/^://')
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
      sleep 1
      echo "Xft.dpi: 75" | DISPLAY=:$MYSCREEN xrdb -merge 
      WMPID=$!
   }

   mkdir -p target # make log directory if necessary

   for ROUND in $(seq 0 $RETRIES)
   do
      DISPLAY=:$MYSCREEN timeout $TIMEOUT pybot \
         $INCLUDES $EXCLUDES \
         --RunEmptySuite \
         --variable BROWSER:$BROWSER \
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
   done
   # round 0 is first
   ROUND=$(expr $ROUND "+" 1)
   LASTROUND=$(expr $RETRIES "+" 1)
   # exit value is the number of failing testcases OR 124 after timeout
   echo "NOTE: pybot exited with $BOT after round $ROUND/$LASTROUND" >> target/$TEST.out;
   # shut down X and WM if they were started
   test -z "$WMPID" || { kill -9 $WMPID; wait $WMPID; } &>/dev/null;
   test -z "$XPID" || { kill -9 $XPID; wait $XPID; } &>/dev/null;
}

halt() {
   echo "Cleaning up..."
   test -n "$WMPID" && { echo "Closing WM $WMPID"; kill -9 $WMPID &>/dev/null; }
   test -n "$XPID" && { echo "Closing X $XPID"; kill -9 $XPID &>/dev/null; }
   test -z "$LUPISPID" || {
      echo "Shutting down lupapiste $LUPISPID"
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
   echo "Removing stray X lock files"
   rm -rf /tmp/.X[1-9][0-9][0-9]-lock

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

map() {
   OP=$1
   shift
   for ARG in $@; do
      $OP "$ARG"
   done
}

show_stats() {
   echo "---------------------- 8< ----------------------"
   MSG="$@"
   NOW=$(date +'%H:%M:%S %d.%m.%Y')
   test -z "$MSG" && MSG="Waiting for $(jobs | grep run_test | wc -l) threads"
   echo -e "${BRIGHT}$NOW: ${MSG}${DEFAULT}"
   load_warnings
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

git_modification_order() {
   for file in "$@"
   do
      echo $(git log -1 --format="%at" -- "$file") " $file"
   done | sort -n -r | sed -e 's/^[0-9]* *//'
}

# lower priority
renice $NICENESS $$ &>/dev/null

# run tests
echo "Running tests $TESTS"
test -z "$INCLUDES" || echo $INCLUDES
echo $EXCLUDES

# find test cases to pass over to robot
TARGETS=
case "$PARALLEL" in
   "files" )
      # find all number-prefixed robot files
      TARGETS=$(find $TESTS | grep "\/[0-9][^/]*\.robot$" | sort -r)
      ;;
   "args" )
      # files/directories as given, just split them to separate lines
      TARGETS=$(map echo "$TESTS")
      ;;
   * )
      fail "Unsupported parallel style '$PARALLEL'"
      ;;
esac

# sort the test suites numerically or by modification date 
case "$ORDER" in 
   "numeric")
      TARGETS=$(for TARGET in "$TARGETS"; do echo $TARGET; done | sort);
      ;;
   "modified")
      # run last modified tests first
      TARGETS=$(git_modification_order $TARGETS)
      #TARGETS=$(echo "$TARGETS" | xargs ls -1 -t)
      ;;
   "random")
      # tests in random order
      TARGETS=$(echo "$TARGETS" | sort -R)
      ;;
   *)
      fail "Unknown test order '$ORDER'. Expected numeric, modified or random."
      ;;
esac

for test in $TARGETS
do
   RUNNING=$(n_tests)
   while [ $RUNNING -ge $MAXTHREADS ]
   do
      show_stats "$RUNNING/$MAXTHREADS threads running"
      for foo in $(seq $UPDATE)
      do
         test $(n_tests) "=" "$RUNNING" || break
         sleep 1
      done
      RUNNING=$(n_tests)
   done
   test -n "$BLACKLIST" && grep -q "$test" "$BLACKLIST" && {
      echo "WARNING: skippin blacklisted test $test";
      continue; }
   
   tag_found=
   if [[ ! -z "$INCLUDE_TAGS" ]]; then
      for tag in $INCLUDE_TAGS; do
         grep -q -r "\[Tags]  .*$tag" "$test" && tag_found=1
      done
   else
      tag_found="irrelevant"
   fi
   
   if [[ -z "$tag_found" ]]; then
      echo "WARNING: tag(s) $INCLUDE_TAGS not found, skipping $test"
      continue
   fi
      
   TEST=$(echo $test | sed -e 's/[/ ]/_/g')
   echo " - Starting $test"
   run_test $SCREEN "$test" &
   SCREEN=$(expr $SCREEN + 1)
   sleep 1
done

# wait for last threads to finish
while true
do
   test $(n_tests) "=" 0 && break
   show_stats
   for foo in $(seq $UPDATE)
   do
      test $(n_tests) "=" 0 && break
      sleep 1
   done
done

show_stats

halt

echo "Tests finished."
