LUPAPISTE.ApplicationGuestsModel = function( params ) {
  "use strict";

  var self = this;

  self.allGuests = ko.observableArray();

  function appId() {
    return lupapisteApp.models.application.id();
  }

  function fetchGuests() {
    ajax.query( "application-guests",
                {id: appId()})
    .success( function( res ) {
      self.allGuests( res.guests );
    })
    .call();
  }

  self.allGuestAuthorities = ko.observableArray();

  function fetchGuestAuthorities() {
    ajax.query( "guest-authorities-application-organization",
              {id: appId()})
    .success( function( res ) {
      self.allGuestAuthorities( res.guestAuthorities );
    })
    .call();
  }

  hub.subscribe( "application-model-updated", function() {
    if( self.isAuthority()) {
      fetchGuestAuthorities();
    }
    fetchGuests();
  });

  // Resolved list of of guest authorities that only
  // only include those authorities that have not yet
  // been given access
  self.guestAuthorities = ko.pureComputed( function() {
    var used = _.reduce( self.allGuests(),
                       function( acc, g ) {
                         if( g.role === "guestAuthority") {
                           acc[g.email] = true;
                           return acc;
                         }
                       }, {} );
    return  _.filter( self.allGuestAuthorities(),
                      function( ga ) {
                        return !used[ga.email];
                      });
  });

  self.nameAndUsername = _.template( "<%- name %> (<%- username %>)");

  self.subscriptionLinkText = function( unsubscribed ) {
    return loc( "application-guests."
              + (unsubscribed ? "subscribe" : "unsubscribe"));
  };

  self.subscriptionLinkClick = function( data ) {
    ajax.command( "toggle-guest-subscription",
                  {id: appId(),
                   username: data.username,
                   "unsubscribe?": !data.unsubscribed})
    .success( fetchGuests)
    .call();
  };

  self.canModify = function( data ) {
    return self.isAuthority() || data.role === "guest";
  };

  self.deleteGuest = function( data ) {
    ajax.command( "delete-guest-application",
                {id: appId(), username: data.username})
    .success( fetchGuests)
    .call();
  };



  self.isAuthority = ko.pureComputed( function() {
    return lupapisteApp.models.currentUser.isAuthority();
  });



  self.emailId = _.uniqueId( "guest-email-");
  self.messageId = _.uniqueId( "guest-message-");

  self.bubbleVisible = ko.observable( false );
  self.waiting = ko.observable( false );

  self.guestError = ko.observable();

  self.error = ko.pureComputed( function() {
    return self.guestError()
        || (self.isAuthority () && self.guestAuthorities ().length
           ? null : "application-guests.no-more-authorities");
  });

  self.toggleBubble = function() {
    self.bubbleVisible( !self.bubbleVisible());
  };

  self.email = ko.observable();

  self.sendEnabled = ko.pureComputed( function() {
    return util.isValidEmailAddress( self.email());
  });


  self.message = ko.observable( loc( "application-guests.message.default"));

  ko.computed( function() {
    if( !self.bubbleVisible()) {
      self.email( "");
      self.message( loc( "application-guests.message.default") );
      self.guestError( null );
    }
  });

  function errorFun( res ) {
    self.guestError( res.text );
  }

  self.send = function() {
    ajax.command( "invite-guest",
                  {email: self.email(),
                   text: self.message(),
                   id: appId(),
                   role: self.isAuthority() ? "guestAuthority" : "guest"} )
    .pending( self.waiting)
    .success( function() {
      fetchGuests();
      self.bubbleVisible( false );
    })
    .error( errorFun )
    .call();
  };
};
