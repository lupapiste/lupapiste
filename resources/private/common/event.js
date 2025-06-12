/**
 * Cross browser compatibility layer for using events.
 * Provides:
 *  - target property
 *  - preventDefault method
 *  - stopPropagation method
 */
function getEvent(event) {
  "use strict";

  var e = event || window.event;
  if (!e) {
    warn("Unable to determine event");
    e = {};
  }

  if (!e.target) {
    e.target = e.srcElement || e;
  }

  if (typeof e.preventDefault !== "function") {
    e.preventDefault = function() {
      e.returnValue = false;
    };
  }

  if (typeof e.stopPropagation  !== "function") {
    e.stopPropagation  = function() {
      e.cancelBubble = true;
    };
  }

  return e;
}
