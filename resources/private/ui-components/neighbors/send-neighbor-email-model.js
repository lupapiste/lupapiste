// Contents for the send neighbor email dialog.
// Params [optional]:
//   name: Neighbor (owner) name
//   [email]: Neighbor email, can be empty/missing.
LUPAPISTE.SendNeighborEmailModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.name = params.name;
  self.email = ko.observable( params.email );
  self.error = ko.observable();
  var neighborId = params.neighborId;
  var appId = lupapisteApp.models.application.id();

  self.ok = self.disposedPureComputed(function() {
    return util.isValidEmailAddress(self.email());
  });

  self.required = self.disposedPureComputed( function() {
    return _.isBlank( self.email() );
  });

  self.badEmail = self.disposedPureComputed( function() {
    return !( self.required() || self.ok() );
  });

  self.close = _.wrap( "close-dialog", hub.send );

  function reload() {
    self.close();
    repository.load( appId, pageutil.makePendingAjaxWait(loc("neighbors.sendEmail.reloading")));
  }

  self.send = function() {
    if( self.ok() ) {
      ajax
        .command("neighbor-send-invite", {id: appId,
                                          email: self.email(),
                                          neighborId: neighborId})
        .pending(pageutil.makePendingAjaxWait(loc("neighbors.sendEmail.sending")))
        .success( reload )
        .error( function( res ) {
          if( res.text === "error.neighbor-marked-done" ) {
            reload();
          } else {
            self.error( res.text );
          }
        })
        .call();
    }
    return false;
  };
};
