var log = function() {

  var levelName = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"];

  var getCallerName = function(caller) {
    var callerName;

    if (typeof caller === "undefined" || caller === null) {
      callerName = "<root>";
    } else if (typeof caller.name !== "undefined") {
      callerName = caller.name;
    } else {
      // IE developer tools:
      // Parse caller name from function definition
      var callerDefinition = caller.toString();
      callerDefinition = callerDefinition.substr('function '.length);
      // Surround with spaces for readability
      callerName = " " + callerDefinition.substr(0, callerDefinition.indexOf('(')) + " ";
    }

    return callerName;
  };

  var logv = (typeof console === "undefined") ? function() {} : function (level, caller, args) {
    if (level < log.limit) {
      return;
    }
    console.log(levelName[level], getCallerName(caller), args);
  };

  return {
    log:       logv,
    TRACE:     0,
    DEBUG:     1,
    INFO:      2,
    WARN:      3,
    ERROR:     4,
    limit:     1,
    setLevel:  function(newLevel) { log.limit = newLevel; }
  };

}();

function trace() { var a = Array.prototype.slice.call(arguments); log.log(log.TRACE, trace.caller, a); }
function debug() { var a = Array.prototype.slice.call(arguments); log.log(log.DEBUG, debug.caller, a); }
function info()  { var a = Array.prototype.slice.call(arguments); log.log(log.INFO,  info.caller,  a); }
function warn()  { var a = Array.prototype.slice.call(arguments); log.log(log.WARN,  warn.caller,  a); }
function error() { var a = Array.prototype.slice.call(arguments); log.log(log.ERROR, error.caller, a); }
