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
  var followed  = [];
  var popNames  = {};

  function getHash() {
    // Null-safe regarding location.
    return _.get( window, "location.hash");
  }

  function hashSupported( options ) {
    return options.hash
      && (!options.followed
          || _.find( followed, function( re ) {
            return re.test( options.hash );
          }) );
  }
  // Options [optional]:
  //  [override]: If true the current position wll override the
  //  possibly stored one (default false).
  //  [followed]: if true the push only happens if hash is followed (default false).
  //  [hash]: Page hash (default window.location.hash).
  self.push = function( options ) {
    options = _.defaults( options, {override: false,
                                    followed: false,
                                    hash: getHash()});
    if( hashSupported( options)
        && ( !positions[options.hash] || options.override ) ) {
          // scrollX/Y not supported by IE.
          positions[options.hash] = {x: window.scrollX || window.pageXOffset,
                                     y: window.scrollY || window.pageYOffset};
    }
  };

  function doPop( options ) {
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

  // Options [optional]
  // [delay]: delay in milliseconds before scrolling (default 1).
  // [name]: If the hash is named (via setName) it can only popped
  // with the name.
  self.pop = function( options ) {
    options = options || {};
    var name = popNames[getHash()];
    if( !name || name === options.name ) {
      doPop( options);
    }
  };

  // Page is followed if its hash matches any of the follow regexps.
  // Followed page positions are automatically pushed when navigated out.
  // See hashChanged function in app.js for details.
  self.follow = function( options ) {
    var hashRe = _.get( options, "hashRe");
    if( _.isRegExp( hashRe)) {
      followed = _.union( followed, [hashRe]);
    }
  };

  // Sets name for hash. Named hashes can only popped with name.
  // Options [optional]:
  // name: Name for hash
  // [hash]: Hash to be named (default window.location.hash);
  self.setName = function( options ) {
    popNames[_.get( options, "hash", getHash())] = options.name;
  };

  // There really should be a better way to restore
  // the scroll position than waiting for 500 ms and hoping
  // that everything has been rendered.
  hub.subscribe( "application-model-updated", _.partial( self.pop, {delay: 500} ) );
  hub.subscribe( self.serviceName + "::push", self.push );
  hub.subscribe( self.serviceName + "::pop", self.pop);
  hub.subscribe( self.serviceName + "::follow", self.follow);
  hub.subscribe( self.serviceName + "::setName", self.setName);
};
