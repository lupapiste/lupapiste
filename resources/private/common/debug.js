$(function() {
  "use strict";

  function applyMinimal(e) {
    ajax.get(window.location.protocol + "//" + window.location.host + "/api/query/apply-fixture")
      .param("name", "minimal")
      .success(function() { $(e.target).parent().find("span").text(" DONE!").show().delay(1000).fadeOut(); })
      .call();
    return false;
  }
  
  function throttle(type, e) {
    var t = $(e.target);
    window.t = t;
    t.parent().find("b.dev-throttle-" + type).text(t.val());
  }
  
  $("footer")
    .append($("<div>").addClass("dev-debug")
    .append($("<h3>")
        .append($("<a>").attr("href", "#").text("Development").click(function() { $("footer .dev-debug div").slideToggle(); return false; })))
    .append($("<div>")
        .append($("<input type='checkbox' checked='checked'>").click(function() { $(".todo").toggleClass("todo-off"); }))
        .append($("<label>").text("Unfinished"))
        .append($("<br>"))
        .append($("<input type='checkbox'>").click(function() { $(".page").toggleClass("visible"); }))
        .append($("<label>").text("Toggle hidden"))
        .append($("<br>"))
        .append($("<a>").attr("href", "#").text("Apply minimal!").click(applyMinimal))
        .append($("<span>").css("font-weight", "bold").hide())
        .append($("<br>"))
        .append($("<span>").text("Throttle web: ")).append($("<b>").addClass("dev-throttle-web").text("0"))
        .append($("<input type='range' value='0' min='0' max='2000' step='20'>").change(_.partial(throttle, "web")))
        .append($("<br>"))
        .append($("<span>").text("Throttle DB: ")).append($("<b>").addClass("dev-throttle-db").text("0"))
        .append($("<input type='range' value='0' min='0' max='2000' step='20'>").change(_.partial(throttle, "db")))));
  
  // Helper function to execute xpath queries. Useful for testing xpath declarations in robot files.
  window.xpath = function(p) { return document.evaluate(p, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; };
  
});
