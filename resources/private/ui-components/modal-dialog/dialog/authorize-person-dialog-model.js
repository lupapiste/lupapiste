// Component for docgen invitations. Used by DocgenPersonSelectModel.
// Parameters [optional]:

// [email]: If email is given it cannot be changed. Also, the
// remove-auth functionality is supported only if the email has been
// given.
// documentName: Document schema name
// path: Party path within the document
// documentId: Document id.
LUPAPISTE.AuthorizePersonDialogModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var fixedEmail = util.isValidEmailAddress( params.email );
  var applicationId = lupapisteApp.services.contextService.applicationId();
  var processing = ko.observable();
  var pending = ko.observable();

  self.description = fixedEmail
    ? "document.party.person-select.designer.areyousure"
    : "invite.desc";
  self.emailId = _.uniqueId( "email-");
  self.email = ko.observable( params.email );
  self.textId = _.uniqueId( "text-");
  self.text = ko.observable( fixedEmail ? "" : loc( "invite.default-text") );
  self.error = ko.observable();
  self.showRemove = ko.observable( false );

  self.allDisabled = self.disposedComputed( function() {
    return processing() || pending();
  });

  self.emailDisabled = self.disposedComputed( function() {
    return self.allDisabled() || fixedEmail;
  });

  function username() {
    return _.toLower( _.trim( self.email()));
  }

  self.inviteDisabled = self.disposedComputed( function() {
    return self.allDisabled()
      || !util.isValidEmailAddress( username() )
      || _.isBlank( self.text() );
  });

  function resize() {
    _.delay( hub.send, 100, "resize-dialog" );
  }

  function errorHandler( res ) {
    self.error( res.text );
    self.showRemove( fixedEmail && _.includes(["reader", "guest"],
                                              res["existing-role"]));
    resize();
  }

 self.invite = function() {
    ajax.command( "invite-with-role",
                  {id: applicationId,
                   documentName: params.documentName,
                   documentId: params.documentId,
                   path: params.path,
                   email: username(),
                   role: "writer",
                   text: _.trim( self.text() )})
      .processing( processing )
      .pending( pending )
      .success( function() {
        repository.load( applicationId );
        hub.send( "close-dialog" );
      })
      .error( errorHandler )
      .call();
  };

  self.remove = function() {
    ajax.command( "remove-auth", {id: applicationId,
                                  username: username()})
      .processing( processing )
      .pending( pending )
      .success( function() {
        repository.load( applicationId );
        hub.send( "refresh-guests");
        self.error( null );
        self.showRemove( false );
        resize();
      })
      .error( errorHandler )
      .call();
  };
};
