$(function() {
  $(".todo").addClass("todo-off");
  $("#todo").click(function() { $(".todo").toggleClass("todo-off"); });
  $("#hidden").click(function() { $(".page").toggleClass("visible"); });
  $("#events").click(function() { hub.send("toggle-show-events"); });
});
