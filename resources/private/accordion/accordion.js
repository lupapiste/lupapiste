var accordion = (function () {

  var animationTime = 200;
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
      content
        .attr("data-accordion-state", state)
        .animate({ height: height }, animationTime, animationEasing);
      setTimeout( function(){content.removeClass("content_expanded")}, animationTime );
      $(e.target).children().removeClass("icon-expanded");
      $(e.target).children().addClass("icon-collapsed");
    } else {
      state = "open";
      content.addClass("content_expanded");
      $(e.target).children().removeClass("icon-collapsed");
      $(e.target).children().addClass("icon-expanded");
      content
        .attr("data-accordion-state", state)
        .animate({ height: height }, animationTime, animationEasing);
    }
    e.preventDefault();
    return false;
  }

  return {
    toggle: toggle
  };

})();
