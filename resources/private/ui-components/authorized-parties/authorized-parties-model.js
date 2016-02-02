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

  self.subscriptionOn = function( role ) {
    var unsub = role.unsubscribed || _.noop;
    return !unsub();
  };

  self.showInviteButton = function() {
    return hasAuth( "invite-with-role");
  };

  self.error = ko.observable();
  self.waiting = ko.observable();

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

  function ajaxInvite( command, params ) {
    ajax.command(command, params )
    .pending( self.waiting )
    .success( function() {
      hub.send( "bubble-dialog", {id: "close all"});
      // It would be better to implement a service for authorized parties,
      // instead of repository.load
      repository.load(application().id());
    })
    .error( function( res ) {
      self.error( res.text );
    })
    .call();
  }

  var hubIds = [];

  hubIds.push( hub.subscribe( "bubble-person-invite", function( params ) {
    ajaxInvite( "invite-with-role",
                _.defaults( params, {id: application().id(),
                                     documentName: "",
                                     documentId: "",
                                     path: "",
                                     role: "writer"} ));
                              } ));

  hubIds.push( hub.subscribe( "bubble-company-invite", function( params ) {
    ajaxInvite( "company-invite",
                _.defaults( params, {id: application().id()}));
  }));

  self.dispose = function() {
    _.map( hubIds, hub.unsubscribe );
    // Dependencies on global observables must be
    // explicitly disposed.
    self.authorizedParties.dispose();
  };


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
