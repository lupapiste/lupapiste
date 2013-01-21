$(function() {
  "use strict";

  $("footer").prepend('<label class="checkbox"><input type="checkbox" id="todo" checked="checked">N\u00e4yt\u00e4 toteuttamattomat</label>' +
      '<label class="checkbox"><input type="checkbox" id="hidden">K\u00e4\u00e4nn\u00e4 piilotetut</label>'+
      '<label class="checkbox"><input type="checkbox" id="events">N\u00e4yt\u00e4 eventit</label>');

  $(".todo").addClass("todo-off");
  $("#todo").click(function() { $(".todo").toggleClass("todo-off"); });
  $("#hidden").click(function() { $(".page").toggleClass("visible"); });
  $("#events").click(function() { hub.send("toggle-show-events"); });
  
  // Helper function to execute xpath queries. Useful for testing xpath declarations in robot files.
  window.xpath = function(p) { return document.evaluate(p, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; };

});
