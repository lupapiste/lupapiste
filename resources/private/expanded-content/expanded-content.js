var expand = (function($) {
  "use strict";

  function click(event) {
    var e = getEvent(event);
    e.preventDefault();
    e.stopPropagation();
    $(e.target).next(".expanded-content").toggleClass("expand");
    if ($(e.target).next(".expanded-content").hasClass("expand")) {
      $(".tab-content-container").css("width", "80%");
      $(e.target).removeClass("drill-right-grey");
      $(e.target).addClass("drill-down-grey");
    } else {
      $(".tab-content-container").css("width", "100%");
      $(e.target).addClass("drill-right-grey");
      $(e.target).removeClass("drill-down-grey");
    }
  }

  return {
    click: click
  };

})(jQuery);
