LUPAPISTE.PersonInviteModel = function( params ) {
  "use strict";
  var self = this;

  self.bubbleVisible = params.bubbleVisible;

  self.emailId = _.uniqueId( "person-invite-email-");
  self.messageId = _.uniqueId( "person-invite-message-");

  self.email = ko.observable();
  self.message = ko.observable();
  self.waiting = ko.observable();
  self.error = ko.observable();

  self.sendEnabled = ko.pureComputed( function() {
    return util.isValidEmailAddress( self.email());
  });

  self.init = function() {
    self.email( "" );
    self.message(  loc( "invite.default-text" ));
    self.error( false );
    self.waiting( false );
  };

  function appId() {
    return lupapisteApp.models.application.id();
  }

  self.send = function() {
    ajax.command( "invite-with-role", {id: appId(),
                                       documentName: params.documentName || "",
                                       documentId: params.documentId || "",
                                       path: params.path || "",
                                       role: "writer",
                                       email: self.email(),
                                       text: self.message()})
    .pending( self.waiting )
    .success( function() {
      self.bubbleVisible( false );
      // It would be better to implement a service for authorized parties,
      // instead of repository.load
      repository.load(appId());
    })
    .error( function( res ) {
      self.error( res.text );
    })
    .call();
  };

  self.dispose = self.sendEnabled.dispose;
};
