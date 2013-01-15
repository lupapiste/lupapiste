;(function() {
  "use strict";
  
  var levelName = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"];
  var limit = 1;
  
  var logv = (typeof console === "undefined") ? function() {} : function (level, args) {
    if (level >= limit) console.log(levelName[level], args);
  };

  window.trace = function() { logv(0, Array.prototype.slice.call(arguments)); };
  window.debug = function() { logv(1, Array.prototype.slice.call(arguments)); };
  window.info  = function() { logv(2, Array.prototype.slice.call(arguments)); };
  window.warn  = function() { logv(3, Array.prototype.slice.call(arguments)); };
  window.error = function() { logv(4, Array.prototype.slice.call(arguments)); };
  
})();
