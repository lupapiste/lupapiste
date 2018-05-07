(function($) {
  "use strict";

  function displayTimingData() {
    var data = [["fetch", "fetchStart", "requestStart"],
                ["req", "requestStart", "responseStart"],
                ["resp", "responseStart", "responseEnd"],
                ["network", "fetchStart", "responseEnd"],
                ["display", "responseEnd", "loadEventEnd"],
                ["total", "navigationStart", "loadEventEnd"]];

    var table = $("footer table.dev-debug-timing");

    if (table.length) {
      _.each(data, function(row) {
        var name = row[0],
        start = window.performance.timing[row[1]],
        end = window.performance.timing[row[2]],
        duration = end - start;
        if (typeof start !== "number") {throw "Unknown timineg event: " + row[1]; }
        if (typeof end !== "number") {throw "Unknown timineg event: " + row[2]; }
        table.append($("<tr>").css("padding", "0px")
             .append($("<td>").text(name).css("padding", "0px"))
             .append($("<td>").text(duration).css("padding", "0px").css("text-align","right")));
      });
    }
  }

  function loadTimingData() {
    if (!window.performance || window.location.pathname.match(/upload/)) {
      return;
    }

    if (!window.performance.timing || !window.performance.timing.loadEventEnd) {
      setTimeout(loadTimingData, 10);
      return;
    }

    if (util.getIn(LUPAPISTE, ["config", "mode"]) === "dev") {
      displayTimingData();
    }

    // window.performance.timing to JSON conversion
    // might be the causing "Cannot convert a Symbol value to a string"
    // errors on Chrome 46?
    ajax.command("browser-timing", {timing: window.performance.timing, pathname: window.location.pathname})
      .error(_.noop)
      .fail(_.noop)
      .call();
  }

  window.addEventListener("load", loadTimingData);

})(jQuery);
