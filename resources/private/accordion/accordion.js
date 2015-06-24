var accordion = (function() {
  "use strict";

  var animationTime = 200,
      animationEasing = "easeInOutCubic";

  function set(t, toState, done) {
    var target = t;
    if (target.is("span")) { target = target.parent(); }

    var content = target.siblings(".accordion_content");

    var state = content.attr("data-accordion-state");
    if (toState === state) { return; }
    if (toState === "toggle") {
      toState = (state !== "closed") ? "closed" : "open";
    }
    state = toState;

    target
      .children(".toggle-icon")
      .removeClass(state === "closed" ? "drill-down-white" : "drill-right-white")
      .addClass(state === "closed" ? "drill-right-white" : "drill-down-white");

    var complete = function() {
      content.css("overflow", "visible");
      target.trigger("accordion-" + state);
      if (done) {
        done(target);
      }
    };

    content.attr("data-accordion-state", state);
    if (state !== "closed") {
      content.slideDown(animationTime, animationEasing, complete);
    } else {
      content.slideUp(animationTime, animationEasing, complete);
    }

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

  function toggleAll(target) {
    $(target).closest(".application_section").find("section.accordion h2").click()
  }

  $.fn.accordionOpen   = function(done) { return open(this, done); };
  $.fn.accordionClose  = function(done) { return close(this, done); };
  $.fn.accordionToggle = function(done) { return toggle(this, done); };

  return {
    open:   open,
    close:  close,
    toggle: toggle,
    click:  click,
    toggleAll: toggleAll
  };

})();
