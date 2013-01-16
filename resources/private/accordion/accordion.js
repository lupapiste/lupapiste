var accordion = (function() {
  "use strict";

  var animationTime = 200;
  var animationEasing = "easeInOutCubic";

  function toggle(event) {
    var e = getEvent(event);
    e.preventDefault();
    e.stopPropagation();
    var target = $(e.target);

    if (target.hasClass("font-icon")) target = target.parent();

    var content = target.next();

    var state = content.attr("data-accordion-state");
    var height = content.attr("data-accordion-height");

    if (!height) {
      // No saved height. Get the current height and save it.
      height = content.height() + "px";
      content.attr("data-accordion-height", height).css("height", height);
    }

    if (state !== "closed") {
      state = "closed";
      height = "0px";
      content
        .attr("data-accordion-state", state)
        .animate({ height: height }, animationTime, animationEasing);
      setTimeout(function() { content.removeClass("content_expanded"); }, animationTime);
      target.children().removeClass("icon-expanded").addClass("icon-collapsed");
    } else {
      state = "open";
      content.addClass("content_expanded");
      target.children().removeClass("icon-collapsed").addClass("icon-expanded");
      content
        .attr("data-accordion-state", state)
        .animate({ height: height }, animationTime, animationEasing);
    }

    return false;
  }

  return {
    toggle: toggle
  };

})();
