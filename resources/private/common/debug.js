$(function() {
  "use strict";

  $("footer")
    .append("<div class=\"dev-debug\">"+
            "<h3><u>Development</u></h3>"+
            "<input type='checkbox' id='debug-todo' checked='checked'>Toteuttamattomat<br/>"+
            "<input type='checkbox' id='debug-hidden'>K&auml;&auml;nn&auml; piilotetut<br/>"+
            "<input type='checkbox' id='debug-events'>N&auml;yt&auml; eventit<br/>"+
            "<a id='debug-apply-minimal' href='#' style='margin-left: 10px'>Apply minimal</a>"+
            "<span id='debug-apply-done' style='display: none'> DONE!</span><br/>"+
            "</div>");

  $(".todo").addClass("todo-off");
  $("#debug-todo").click(function() { $(".todo").toggleClass("todo-off"); });
  $("#debug-hidden").click(function() { $(".page").toggleClass("visible"); });
  $("#debug-events").click(function() { hub.send("toggle-show-events"); });
  $("#debug-apply-minimal").click(function() {
    ajax.get(window.location.protocol + "//" + window.location.host + "/api/query/apply-fixture")
      .param("name", "minimal")
      .success(function() {
        $("#debug-apply-done").show();
      })
      .call();
    return false;
  });

  hub.subscribe("page-change", function() { $("#debug-apply-done").hide(); });

  // Helper function to execute xpath queries. Useful for testing xpath declarations in robot files.
  window.xpath = function(p) { return document.evaluate(p, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; };

});
