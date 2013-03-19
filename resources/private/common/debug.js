$(function() {
  "use strict";

  function applyFixture(fixture) {
    ajax.get(window.location.protocol + "//" + window.location.host + "/api/query/apply-fixture")
      .param("name", fixture)
      .param("npm", "true")
      .success(function() { $("#debug-apply-done").text(" DONE!").show().delay(1000).fadeOut(); })
      .call();
    return false;
  }

  function throttle(type, e) {
    var t = $(e.target);
    var value = t.val();
    ajax.post(window.location.protocol + "//" + window.location.host + "/perfmon/throttle/" + type)
      .raw()
      .json({value: value})
      .header("npm", "true")
      .success(function() { t.parent().find("b.dev-throttle-" + type).text(value); })
      .call();
  }

  function loadTimingData() {
    if (!window.performance) { return; }

    if (!window.performance.timing.loadEventEnd) {
      setTimeout(loadTimingData, 10);
      return;
    }

    var table = $("footer table.dev-debug-timing");
    var data = [["fetch", "fetchStart", "requestStart"],
                ["req", "requestStart", "responseStart"],
                ["resp", "responseStart", "responseEnd"],
                ["network", "fetchStart", "responseEnd"],
                ["display", "responseEnd", "loadEventEnd"],
                ["total", "navigationStart", "loadEventEnd"]];

    _.each(data, function(row) {
      var name = row[0],
          start = window.performance.timing[row[1]],
          end = window.performance.timing[row[2]],
          duration = end - start;
      if (typeof start !== "number") {throw "Unknown timineg event: " + row[1]; }
      if (typeof end !== "number") {throw "Unknown timineg event: " + row[2]; }
      table
        .append($("<tr>").css("padding", "0px")
          .append($("<td>").text(name).css("padding", "0px"))
          .append($("<td>").text(duration).css("padding", "0px").css("text-align","right")));
    });

    ajax.post(window.location.protocol + "//" + window.location.host + "/perfmon/browser-timing")
      .raw()
      .json({timing: window.performance.timing})
      .header("npm", "true")
      .call();
  }

  $("footer")
    .append($("<div>").addClass("dev-debug")
      .append($("<h3>")
        .append($("<a>").attr("href", "#").text("Development").click(function() { $("footer .dev-debug div:eq(0)").slideToggle(); return false; })))
      .append($("<div>")
        .append($("<input id='debug-tab-flow' type='checkbox'>").click(function() { hub.send("set-debug-tab-flow", { value: !!$(this).attr("checked") }); }))
        .append($("<label>").text("Flowing tabs"))
        .append($("<br>"))
        .append($("<input type='checkbox' checked='checked'>").click(function() { $(".todo").toggleClass("todo-off"); }))
        .append($("<label>").text("Unfinished"))
        .append($("<br>"))
        .append($("<input type='checkbox'>").click(function() { $(".page").toggleClass("visible"); }))
        .append($("<label>").text("Toggle hidden"))
        .append($("<br>"))
        .append($("<input type='checkbox' data-id='proxy'>").click(function(e) { ajax.post("/api/proxy-ctrl/" + ($(e.target).prop("checked") ? "on" : "off")).call(); }))
        .append($("<label>").text("Proxy enabled"))
        .append($("<p>").text("Apply:")
          .append($("<span>").attr("id", "debug-apply-done").css("font-weight", "bold").hide())
          .append($("<br>"))
          .append($("<a>").attr("id", "debug-apply-minimal").attr("href", "#").text("minimal").click(function() { applyFixture("minimal"); }))
          .append($("<br>"))
          .append($("<a>").attr("id", "debug-apply-minimal").attr("href", "#").text("municipality-test-users").click(function() { applyFixture("municipality-test-users"); })))
        .append($("<span>").attr("id", "debug-apply-done").css("font-weight", "bold").hide())
        .append($("<br>"))
        .append($("<span>").text("Throttle web: "))
        .append($("<b>").addClass("dev-throttle-web").text("0"))
        .append($("<input type='range' value='0' min='0' max='2000' step='10'>").change(_.throttle(_.partial(throttle, "web"), 500)))
        .append($("<br>"))
        .append($("<span>").text("Throttle DB: "))
        .append($("<b>").addClass("dev-throttle-db").text("0"))
        .append($("<input type='range' value='0' min='0' max='2000' step='10'>").change(_.throttle(_.partial(throttle, "db"), 500))))
      .append($("<h3>")
        .append($("<a>").attr("href", "#").text("Timing").click(function() { $("footer .dev-debug div:eq(1)").slideToggle(); return false; })))
      .append($("<div>")
        .append($("<table>").addClass("dev-debug-timing"))
        .hide()));

  setTimeout(loadTimingData, 10);

  ajax.get(window.location.protocol + "//" + window.location.host + "/perfmon/throttle")
    .success(function(data) {
      var ranges = $("footer .dev-debug input[type='range']");
      $(ranges[0]).val(data.web).change();
      $(ranges[1]).val(data.db).change();
    })
    .call();

  ajax
    .get("/api/proxy-ctrl")
    .success(function(data) { $("footer input[data-id='proxy']").prop("checked", data.data ? "checked" : ""); })
    .call();
  
  // Helper function to execute xpath queries. Useful for testing xpath declarations in robot files.
  window.xpath = function(p) { return document.evaluate(p, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; };

});
