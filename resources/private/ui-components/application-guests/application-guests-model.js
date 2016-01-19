LUPAPISTE.ApplicationGuestsModel = function( params ) {
  "use strict";

  var self = this;

  self.isAuthority = ko.pureComputed( function() {
    return lupapisteApp.models.currentUser.isAuthority();
  });

    self.guestAuthorities = [{name: "Hello world",
                            email: "hello@world.com",
                            role: "shijie"},
                           {name: "Foo Bar",
                            email: "foo@bar.com",
                            role: "Baz"}];

  self.emailId = _.uniqueId( "guest-email-");
  self.messageId = _.uniqueId( "guest-message-");

  self.bubbleVisible = ko.observable( false );
  self.waiting = ko.observable( false );

  self.guestError = ko.observable();

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

  function sendInvite( args ) {
    ajax.command( "invite-with-role",
                _.assign( args, {text: self.message(),
                                role: self.isAuthority() ? "guestAuthority" : "guest",
                                path: "",
                                documentId: "",
                                documentName: ""}))
    .pending( self.waiting)
    .success( _.partial( self.bubbleVisible, false))
    .error( errorFun )
    .call();
  }

  self.send = function() {
    var args = {email: self.email(),
                id: lupapisteApp.models.application.id()};
    ajax.query( "add-guest-pseudo-query", args )
    .pending( self.waiting)
    .success( _.partial( sendInvite, args ))
    .error( errorFun )
    .call();
  };
};
