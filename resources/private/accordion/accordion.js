var accordion = (function () {

  var animationTime = 400;
  var animationEasing = "easeInOutCubic";

  function toggle(e) {
    var content = $(e.target).next();

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
    } else {
      state = "open";
    }

    content
      .attr("data-accordion-state", state)
      .animate({ height: height }, animationTime, animationEasing);

    e.preventDefault();
    return false;
  }

  return {
    toggle: toggle
  };

})();
