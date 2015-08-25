;(function() {
  "use strict";

  function nop() {
  }

  function filtered(page, message) {
    var pageFilter = /^(resource:\/|Unknown script code|https:\/\/[a-z1-9]+\.checkpoint\.com\/)/;
    return pageFilter.test(page) || _.include(message, "NPObject");
  }

  var levelName = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"];
  var limit = 1;
  var serverLimit = 4;

  var logv = function (level, args) {
    var message = args.length === 1 ? args[0]: args;

    if (level >= limit && typeof console !== "undefined") {
      console.log(levelName[level], message);
    }

    var page = location.pathname + location.hash;
    if (level >= serverLimit && typeof ajax !== "undefined" && !filtered(page, message)) {
      ajax.command("frontend-error", {page: page, message: message, build: LUPAPISTE.config.build}).fail(nop).error(nop).call();
    }
  };

  window.trace = function() { logv(0, Array.prototype.slice.call(arguments)); };
  window.debug = function() { logv(1, Array.prototype.slice.call(arguments)); };
  window.info  = function() { logv(2, Array.prototype.slice.call(arguments)); };
  window.warn  = function() { logv(3, Array.prototype.slice.call(arguments)); };
  window.error = function() { logv(4, Array.prototype.slice.call(arguments)); };

  window.setLogLimit = function(l) { limit = l; };

  window.onerror = function(msg, url, line, col, error) {
    ajax.query("newest-version", {frontendBuild: "LUPAPISTE.config.build"})
      .onError("frontend-too-old", function() {
        if (confirm("Lupis build too old, reloading page in browser")) {
          window.location.reload();
        }
      })
      .success(_.noop)
      .call();

    if (url) {
      var sourcePosition = (col === undefined) ? line : line + ":" + col;
      var message = (error === undefined || !error.stack) ? msg : msg + " -- Stack: " + error.stack;
      window.error(url + ":" + sourcePosition + " " + message);
    }
    return true;
  };

})();
