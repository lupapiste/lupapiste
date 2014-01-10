var accordion = (function() {
  "use strict";

  var animationTime = 200,
      animationEasing = "easeInOutCubic";

  function set(t, toState, done) {
    var target = t;
    if (target.is("span")) { target = target.parent(); }

    var content = target.next();

    var state = content.attr("data-accordion-state");

    if (toState === state) { return; }

    if (toState === "toggle") { toState = (state !== "closed") ? "closed" : "open"; }

    var height = content.attr("data-accordion-height");
    if (!height) {
      var h = content.height();
      height = h + "px";
      content
        .attr("data-accordion-height", height)
        .css({"height": height, "overflow": "visible"});
    }

    if (toState === "closed") {
      state = "closed";
      height = "0px";
    } else {
      state = "open";
    }

    target
      .children(".toggle-icon")
      .removeClass(toState === "closed" ? "drill-down-white" : "drill-right-white")
      .addClass(toState === "closed" ? "drill-right-white" : "drill-down-white");

    content
      .attr("data-accordion-state", state)
      .animate({ height: height }, animationTime, animationEasing, function() {
        if (state === "closed") {
          content.removeClass("expanded");
        } else {
          content.addClass("expanded");
        }
        target.trigger("accordion-" + state);
        if (done) { done(target); }
      })
      .css('overflow', 'visible');

    return t;
  }

  function open(t, done)   { set(t, "open", done);   return t; }
  function close(t, done)  { set(t, "closed", done); return t; }
  function toggle(t, done) { set(t, "toggle", done); return t; }

  function click(event) {
    var e = getEvent(event);
    e.preventDefault();
    e.stopPropagation();
    toggle($(e.target));
    return false;
  }

  $.fn.accordionOpen   = function(done) { return open(this, done); };
  $.fn.accordionClose  = function(done) { return close(this, done); };
  $.fn.accordionToggle = function(done) { return toggle(this, done); };

  return {
    open:   open,
    close:  close,
    toggle: toggle,
    click:  click
  };

})();
