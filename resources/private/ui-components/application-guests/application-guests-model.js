LUPAPISTE.ApplicationGuestsModel = function() {
  "use strict";

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

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
    return lupapisteApp.services.contextService.applicationId();
  }

  function currentUsername() {
    return lupapisteApp.models.currentUser.username();
  }

  function hasAuth( action ) {
    return lupapisteApp.models.applicationAuthModel.ok( action );
  }

  self.isAuthority = ko.pureComputed( _.partial( hasAuth,
                                                 "guest-authorities-application-organization"));

  // Current focus
  self.focus = {email: ko.observable( !self.isAuthority()),
                message: ko.observable()};


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
      hub.send( "indicator", {style: "positive"});
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
                   unsubscribe: !data.unsubscribed})
    .success( fetchGuests)
    .call();
  };

  function fetchAll() {
    if( self.isAuthority()) {
      fetchGuestAuthorities();
    }
    fetchGuests();
  }

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

  self.canInvite = function() {
    return hasAuth( "invite-guest");
  };

  self.canDelete = function( data ) {
    var result = hasAuth( "delete-guest-application");
    if( result && data && data.role === "guestAuthority") {
      result = self.isAuthority();
    }
    return result;
  };

  self.canSubscribe = function( data ) {
    return self.canInvite()
         ? self.isAuthority() || data.role === "guest"
         : data.username === currentUsername();
  };


  self.error = ko.pureComputed( function() {
    var err = self.guestError();
    if( !err && self.isAuthority() && !_.size( self.guestAuthorities()) ) {
      err =  _.size(self.allGuestAuthorities())
          ? "application-guests.no-more-authorities"
          : "application-guests.no-authorities-defined";
    }
    return err;
  });

  self.toggleBubble = function() {
    self.bubbleVisible( !self.bubbleVisible());
  };

  self.sendEnabled = ko.pureComputed( function() {
    return util.isValidEmailAddress( self.email());
  });

  self.initBubble = function() {
    self.waiting( false );
    self.email( "");
    self.message( loc( "application-guests.message.default") );
    self.guestError( null );
  };

  // Visibility flags

  self.show = {
    subscribeColumn: ko.pureComputed( function() {
      return _.find( self.allGuests(), self.canSubscribe );
    }),
    inviteTable: ko.pureComputed( function() {
      return self.guestAuthorities().length;
    }),
    inviteMessage: ko.pureComputed( function() {
      return !self.isAuthority() || self.guestAuthorities().length;
    }),
    mandatory: ko.pureComputed( _.negate( self.sendEnabled) )
  };

  if( appId() ) {
    fetchAll();
  }

  self.addHubListener( "contextService::enter", fetchAll );
  self.addHubListener( "refresh-guests", fetchAll );
};
