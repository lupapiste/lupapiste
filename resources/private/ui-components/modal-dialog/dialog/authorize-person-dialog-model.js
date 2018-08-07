LUPAPISTE.AuthorizePersonDialogModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var fixedEmail = util.isValidEmailAddress( params.email );
  var applicationId = lupapisteApp.services.contextService.applicationId();
  var processing = ko.observable();
  var pending = ko.observable();

  self.description = params.description;
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

  function errorHandler( res ) {
    self.error( res.text );
    self.showRemove( fixedEmail && _.includes(["reader", "guest"],
                                              res["existing-role"]));
    hub.send( "resize-dialog");
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
        self.error( null );
        self.showRemove( false );
        hub.send( "resize-dialog");
      })
      .error( errorHandler )
      .call();
  };
};
