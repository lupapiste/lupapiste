LUPAPISTE.AuthorizedPartiesModel = function() {
  "use strict";
  var self = this;

  // ---------------------------------------------------
  // Authorized table
  // ---------------------------------------------------

  function isGuest( s ) {
    return _.includes( ["guest", "guestAuthority"], s );
  }

  function application() {
    return lupapisteApp.models.application;
  }

  function hasAuth( id ) {
    return lupapisteApp.models.applicationAuthModel.ok( id );
  }

  var nameTemplate = _.template( "<%- firstName %> <%- lastName %> (<%- username %>)");

  self.nameInformation = function( role ) {
    return nameTemplate( ko.mapping.toJS( role ));
  };

  self.roleInformation = function( role ) {
    return _( role.roles )
           .reject( isGuest )
           .map( _.flow( loc, _.capitalize ))
           .value()
           .join( ", ");
  };

  self.isNotOwner = function( role ) {
    return application().isNotOwner( role );
  };

  self.showRemove = function( role ) {
    return hasAuth( "remove-auth") && self.isNotOwner( role );
  };

  self.showSubscriptionStatus = function( role ) {
    return application().canSubscribe( role );
  };

  self.showSubscribe = function( role ) {
    return role.unsubscribed && role.unsubscribed();
  };

  self.showUnsubscribe = function( role ) {
    return role.unsubscribed && !role.unsubscribed();
  };

  self.showInviteButton = function() {
    return hasAuth( "invite-with-role");
  };

  self.authorizedParties = ko.pureComputed( function() {
   return _( application().roles() )
          .reject( function( role ) {
            // Guests are filtered only if the party does
            // not have any other roles. At least in theory,
            // it is possible that guest is also statementGiver,
            // for example.
            return isGuest( role.role()) && role.roles.length === 1;
          })
          .value();
  });

  // Dependencies on global observables must be
  // explicitly disposed.
  self.dispose = self.authorizedParties.dispose;


  // ---------------------------------------------------
  // Invite person
  // ---------------------------------------------------

  self.personBubble = ko.observable( false );

  self.togglePersonBubble = function() {
    self.personBubble( !self.personBubble());
  };

// ---------------------------------------------------
  // Invite company
  // ---------------------------------------------------

  self.companyBubble = ko.observable( false );

  self.toggleCompanyBubble = function() {
    self.companyBubble( !self.companyBubble());
  };


};
