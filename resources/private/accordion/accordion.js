var accordion = (function() {
  "use strict";

  var animationTime = 200;
  var animationEasing = "easeInOutCubic";

  function nop() { }
  
  function set(t, toState, done) {
    var target = t;
    if (target.is("span")) target = target.parent();

    var content = target.next();
    var state = content.attr("data-accordion-state");

    if (toState === state) return;
    if (toState === "toggle") toState = (state !== "closed") ? "closed" : "open";
    
    console.log("E", "\"" + target.children("span").html() + "\"", "State:", state, "toState", toState);

    var height = content.attr("data-accordion-height");

    if (!height) {
      var h = content.height();
      console.log("!height, h=", h);
      if (h > 10) {
        // No saved height. Get the current height and save it.
        height = h + "px";
        content.attr("data-accordion-height", height).css("height", height);
        console.log("saved:", height);
      } else {
        height = "auto";
        console.log("using:", height);
      }
    }

    var completed = nop;
    
    if (toState === "closed") {
      state = "closed";
      height = "0px";
    } else {
      state = "open";
    }

    target
      .children(".font-icon")
      .removeClass(toState === "closed" ? "icon-expanded" : "icon-collapsed")
      .addClass(toState === "closed" ? "icon-collapsed" : "icon-expanded");
    
    content
      .attr("data-accordion-state", state)
      .animate({ height: height }, animationTime, animationEasing, function() {
        if (state === "closed") {
          content.removeClass("content_expanded");
        } else {
          content.addClass("content_expanded");
        }
        target.trigger(state);
        if (done) done(target);
      });

    return t;
  }
  
  function open(t, done) {
    return set(t, "open", done);
  }

  function close(t, done) {
    return set(t, "closed", done);
  }
  
  function toggle(t, done) {
    return set(t, "toggle", done);
  }
  
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
