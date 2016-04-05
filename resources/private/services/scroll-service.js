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

  self.push = function() {
    var hash = window.location.hash;
    if( !positions[hash]) {
      positions[hash] = {x: window.scrollX, y: window.scrollY };
    }
  };

  self.pop = function( delayMilliseconds ) {
    var hash = window.location.hash;
    var pos = positions[hash];
    delete positions[hash];
    if( pos ) {
      _.delay( _.partial( window.scrollTo, pos.x, pos.y ),
               delayMilliseconds || 1 );
    }
  };

  // There really should be a better way to restore
  // the scroll position than waiting for 500 ms and hoping
  // that everything has been rendered.
  hub.subscribe( "application-model-updated", _.partial( self.pop, 500 ) );
  hub.subscribe( self.serviceName +"::push", self.push );
  hub.subscribe( self.serviceName + "::pop",
                 function( options ) {
                   self.pop( options.delay );
                 });
};
