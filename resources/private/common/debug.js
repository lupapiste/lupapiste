$(function() {

  $("footer").prepend('<label class="checkbox"><input type="checkbox" id="todo" checked="checked">N\u00e4yt\u00e4 toteuttamattomat</label>' +
      '<label class="checkbox"><input type="checkbox" id="hidden">K\u00e4\u00e4nn\u00e4 piilotetut</label>'+
      '<label class="checkbox"><input type="checkbox" id="events">N\u00e4yt\u00e4 eventit</label>');

  $(".todo").addClass("todo-off");
  $("#todo").click(function() { $(".todo").toggleClass("todo-off"); });
  $("#hidden").click(function() { $(".page").toggleClass("visible"); });
  $("#events").click(function() { hub.send("toggle-show-events"); });
});
