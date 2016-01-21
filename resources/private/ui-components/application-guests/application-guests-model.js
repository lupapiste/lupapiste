LUPAPISTE.ApplicationGuestsModel = function( params ) {
  "use strict";

  var self = this;

  self.emailId = _.uniqueId( "guest-email-");
  self.messageId = _.uniqueId( "guest-message-");

  self.email = ko.observable();
  self.message = ko.observable( loc( "application-guests.message.default"));

  self.bubbleVisible = ko.observable( false );
  self.waiting = ko.observable( false );

  self.guestError = ko.observable();

  // Every guest and guestAuthority in the application.
  self.allGuests = ko.observableArray();

 // Guest authorities defined in the application's organization.
  self.allGuestAuthorities = ko.observableArray();

  function appId() {
    return lupapisteApp.models.application.id();
  }

  function hasAuth( action ) {
    return lupapisteApp.models.applicationAuthModel.ok( action );
  }

  self.isAuthority = ko.pureComputed( _.partial( hasAuth,
                                                 "guest-authorities-application-organization"));

  // Ajax calls to backend endpoints

  function fetchGuests() {
    ajax.query( "application-guests",
                {id: appId()})
    .success( function( res ) {
      self.allGuests( res.guests );
    })
    .call();
  }

  function fetchGuestAuthorities() {
    ajax.query( "guest-authorities-application-organization",
              {id: appId()})
    .success( function( res ) {
      self.allGuestAuthorities( res.guestAuthorities );
    })
    .call();
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
    .error( function( res ) {
      self.guestError( res.text );
    } )
    .call();
  };

  self.deleteGuest = function( data ) {
    ajax.command( "delete-guest-application",
                {id: appId(), username: data.username})
    .success( fetchGuests)
    .call();
  };

  self.subscriptionLinkClick = function( data ) {
    ajax.command( "toggle-guest-subscription",
                  {id: appId(),
                   username: data.username,
                   "unsubscribe?": !data.unsubscribed})
    .success( fetchGuests)
    .call();
  };

  // Initialization and reacting to updates outside of
  // the component.
  hub.subscribe( "application-model-updated", function() {
    if( self.isAuthority()) {
      fetchGuestAuthorities();
    }
    fetchGuests();
  });

  // Resolved list of of guest authorities that only include those
  // authorities that have not yet been given access
  self.guestAuthorities = ko.pureComputed( function() {
    var used = _.reduce( self.allGuests(),
                       function( acc, g ) {
                         if( g.role === "guestAuthority") {
                           acc[g.email] = true;
                         }
                         return acc;
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


  self.canModify = function( data ) {
    var result = hasAuth( "delete-guest-application");
    if( result && data && data.role === "guestAuthority") {
      result = self.isAuthority();
    }
    return result;
  };


  self.error = ko.pureComputed( function() {
    var err = self.guestError();
    if( !err && self.isAuthority() && !_.size( self.guestAuthorities()) ) {
      err = "application-guests.no-more-authorities";
    }
    return err;
  });

  self.toggleBubble = function() {
    self.bubbleVisible( !self.bubbleVisible());
  };

  self.sendEnabled = ko.pureComputed( function() {
    return util.isValidEmailAddress( self.email());
  });

  // Reset the dialog contents when it is closed.
  ko.computed( function() {
    if( !self.bubbleVisible()) {
      self.email( "");
      self.message( loc( "application-guests.message.default") );
      self.guestError( null );
    }
  });

  // Visibility flags

  self.show = {
    addButton: function() {
      return hasAuth( "invite-guest");
    },
    inviteTable: ko.pureComputed( function() {
      return self.guestAuthorities().length;
    }),
    inviteMessage: ko.pureComputed( function() {
      return !self.isAuthority() || self.guestAuthorities().length;
    })
  };


};
