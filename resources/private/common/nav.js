hub.subscribe("page-load", function(e) {
  "use strict";
  $("#nav li").removeClass("active");
  $("#nav-" + e.pageId).addClass("active");
});
