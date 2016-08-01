// Simple service for storing/restoring scroll position.
// The positions are stored on the location.hash basis using
// the following heuristics:
//   1. If the hash already has a stored position, new position is
//      ignored.
//   2. Pop operation scrolls to the position stored for the current
//      hash and removes the position from the service.
// These two simple rules make it possible for repository.load to
// retain the scroll position quite successfully while at the same
// time allowing more fine-grained tweaking.
LUPAPISTE.ScrollService = function() {
  "use strict";
  var self = this;

  self.serviceName = "scrollService";

  var positions = {};

  function getHash() {
    // Null-safe regardign location.
    return _.get( window, "location.hash");
  }

  // Options [optional]:
  //  [override]: If true the current position wll override the
  //  possibly stored one (default false).
  self.push = function( options ) {
    options = options || {};
    var hash = getHash();
    if( hash && ( !positions[hash] || options.override ) ) {
      positions[hash] = {x: window.scrollX, y: window.scrollY };
    }
  };

  // Options [optional]
  // [delay]: delay in milliseconds before scrolling (default 1).
  self.pop = function( options ) {
    options = options || {};
    var hash = getHash();
    var pos = positions[hash];
    delete positions[hash];
    if( pos ) {
      _.delay( function() {
        // The page may have changed during delay
        if( hash === getHash()) {
          window.scrollTo(pos.x, pos.y);
        }
      }, options.delay || 1 );
    }
  };

  // There really should be a better way to restore
  // the scroll position than waiting for 500 ms and hoping
  // that everything has been rendered.
  hub.subscribe( "application-model-updated", _.partial( self.pop, {delay: 500} ) );
  hub.subscribe( self.serviceName + "::push", self.push );
  hub.subscribe( self.serviceName + "::pop", self.pop );
};
