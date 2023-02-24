LUPAPISTE.PersonInviteModel = function( params ) {
  "use strict";
  var self = this;

  self.bubbleVisible = params.bubbleVisible;

  self.emailId = _.uniqueId( "person-invite-email-");
  self.messageId = _.uniqueId( "person-invite-message-");

  self.email = ko.observable();
  self.message = ko.observable();
  self.waiting = params.waiting;
  self.error = params.error;

  self.sendEnabled = ko.pureComputed( function() {
    return util.isValidEmailAddress( self.email());
  });

  self.showMandatory = ko.pureComputed( _.negate( self.sendEnabled) );

  self.init = function() {
    self.email( "" );
    self.message(  loc( "invite.default-text" ));
    self.error( false );
    self.waiting( false );
  };

  self.send = function() {
    hub.send( "authorized::bubble-person-invite",
              {invite: {email: self.email(),
                        text: self.message()}});
  };
};
