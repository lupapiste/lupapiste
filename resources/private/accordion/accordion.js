var accordion = (function() {
  "use strict";

  var animationTime = 200,
      animationEasing = "easeInOutCubic";

  var memory = {};

  function selectors( t ) {
    var target = t.closest( ".accordion-toggle");
    var content = target.siblings(".accordion_content");
    return {
      target: target,
      content: content
    };
  }

  function set(t, toState, done, force ) {
    var sels = selectors( t );
    var target = sels.target;
    var content = sels.content;

    var state = content.attr("data-accordion-state");
    if (toState !== state || force ) {
      if (toState === "toggle") {
        toState = (state !== "closed") ? "closed" : "open";
      }
      state = toState;

      var complete = function () {
        content.css("overflow", "visible");
        target.trigger("accordion-" + state);
        target.toggleClass( "toggled", state === "open");
        memory[content.prop( "id" )] = state;
        if (done) {
          done(target);
        }
      };

      content.attr("data-accordion-state", state);
      if (state !== "closed") {
        if (LUPAPISTE.config.features.animations) {
          content.slideDown(animationTime, animationEasing, complete);
        } else {
          content.show();
        }
      } else {
        if (LUPAPISTE.config.features.animations) {
          content.slideUp(animationTime, animationEasing, complete);
        } else {
          content.hide();
        }
      }
    }
    return t;
  }

  function open(t, done)   { return set(t, "open", done); }
  function close(t, done)  { return set(t, "closed", done); }
  function toggle(t, done) { return set(t, "toggle", done); }
  function reset( t, done ) {
    var key = selectors( t ).content.prop( "id");
    var state = memory[key];
    if( state ) {
      set( t, state, done, true );
    }
  }

  function click(event) {
    var e = getEvent(event);
    e.preventDefault();
    e.stopPropagation();
    toggle($(e.target));
    return false;
  }

  function toggleAll(target) {
    $(target).closest(".application_section").find("section.accordion .accordion-toggle").trigger("click");
  }

  $.fn.accordionOpen   = function(done) { return open(this, done); };
  $.fn.accordionClose  = function(done) { return close(this, done); };
  $.fn.accordionToggle = function(done) { return toggle(this, done); };

  return {
    open:   open,
    close:  close,
    toggle: toggle,
    reset: reset,
    click:  click,
    toggleAll: toggleAll

  };

})();

// Collection of accordion isOpen observables.
// See accordion-toolbar component for details.
// Flag values: true is open and false closed.
var AccordionState = (function() {
  "use strict";

  var observables = {};

  function get( docId ) {
    var obs = observables[docId];
    return obs ? Boolean( obs()) : null;
  }
  function register( docId, obs  ) {
    var old = get( docId );
    if( _.isBoolean( old )) {
      obs( old );
    }
    observables[docId] = obs;
  }
  function set( docId, flag ) {
    var obs = observables[docId];
    if( obs && get( docId ) !== flag ) {
      obs( Boolean( flag ));
    }
  }
  function toggleAll( flag ) {
    _.each(_.values(observables), function( obs ) {
      obs( Boolean( flag ));
    });
  }
  function deregister(docId) {
    delete observables[docId];
  }
  return {
    get: get,
    register: register,
    set: set,
    toggleAll: toggleAll,
    deregister: deregister
  };
})();
