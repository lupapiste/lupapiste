var LUPAPISTE = LUPAPISTE || {};
/**
 * A fuse that might be burned (value=true) or intact (value=false).
 * Write null to reset.
 */
LUPAPISTE.Fuse = function() {
  "use strict";
  var burned = ko.observable(false);
  return ko.computed( {
    read: function() {
      return burned();
    },
    write: function(value) {
      burned( value === null ? false : Boolean(value || burned()) );
    }
  });
};
