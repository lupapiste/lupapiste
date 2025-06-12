;(function() {
  "use strict";

  function reloadNotice() {
    if (confirm(loc("error.frontend-outdated"))) {
      window.location.reload();
    }
  }

  var levelName = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"];
  var limit = 1;

  var logv = function (level, args) {
    var message = args.length === 1 ? args[0]: args;

    if (level >= limit && typeof console !== "undefined") {
      // jshint devel: true
      console.log(levelName[level], message);
    }
  };

  window.trace = function() { logv(0, Array.prototype.slice.call(arguments)); };
  window.debug = function() { logv(1, Array.prototype.slice.call(arguments)); };
  window.info  = function() { logv(2, Array.prototype.slice.call(arguments)); };
  window.warn  = function() { logv(3, Array.prototype.slice.call(arguments)); };
  window.error = function() { logv(4, Array.prototype.slice.call(arguments)); };

  window.setLogLimit = function(l) { limit = l; };

  // Limit version check rate to prevent bursts
  var versionCheckedAt = new Date("1900-01-01");
  if (LUPAPISTE.config.mode !== "dev") {
    window.addEventListener("error", function (e) {
      var now = new Date();
      if (now.getTime() - versionCheckedAt.getTime() >= 60000) { // 1 minute
        versionCheckedAt = now;
        ajax.query("newest-version", {frontendBuild: LUPAPISTE.config.build})
          .onError("frontend-too-old", reloadNotice)
          .success(_.noop)
          .call();
      }
      if (e.filename) {
        var error = e.error;
        var msg = e.message;
        var sourcePosition = (e.colno === undefined) ? e.lineno : e.lineno + ":" + e.colno;
        var message = (error === undefined || !error.stack) ? msg : msg + " -- Stack: " + error.stack;
        window.error(e.filename + ":" + sourcePosition + " " + message);
      }
      return true;
    });
  }
})();
