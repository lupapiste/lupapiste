var accordion = (function() {
  "use strict";

  var animationTime = 200,
      animationEasing = "easeInOutCubic";

  function set(t, toState, done) {
    var target = t;
    if (target.not("button")) { target = target.parent(); }

    var content = target.siblings(".accordion_content");

    var state = content.attr("data-accordion-state");
    if (toState != state) {
      if (toState === "toggle") {
        toState = (state !== "closed") ? "closed" : "open";
      }
      state = toState;

      var complete = function () {
        content.css("overflow", "visible");
        target.trigger("accordion-" + state);
        target.toggleClass( "toggled", state === "open");
        if (done) {
          done(target);
        }
      };

      content. attr ("data-accordion-state", state);
      if (state !== "closed") {
        content.slideDown(animationTime, animationEasing, complete);
      } else {
        content.slideUp(animationTime, animationEasing, complete);
      }
    }
    return t;
  }

  function open(t, done)   { return set(t, "open", done); }
  function close(t, done)  { return set(t, "closed", done); }
  function toggle(t, done) { return set(t, "toggle", done); }

  function click(event) {
    console.log( "Target:" , getEvent(e).target);
    var e = getEvent(event);
    e.preventDefault();
    e.stopPropagation();
    toggle($(e.target));
    return false;
  }

  function toggleAll(target) {
    $(target).closest(".application_section").find("section.accordion button").click();
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
