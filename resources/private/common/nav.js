hub.subscribe("page-change", function(e) {
  "use strict";
  $("#nav li").removeClass("active");
  $("#nav-" + e.pageId).addClass("active");
});
