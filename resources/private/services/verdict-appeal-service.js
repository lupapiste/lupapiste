// Service for managing verdict appeals.
LUPAPISTE.VerdictAppealService = function() {
  "use strict";
  var self = this;

  self.serviceName = "verdictAppealService";

  self.allAppeals = {};

  function fetchAllAppeals() {
    ajax.query( "appeals", {id: lupapisteApp.models.application.id()})
      .success( function( res ) {
        console.log( "fetchAllappeals:", res );
        self.allAppeals = res.data;
        hub.send( self.serviceName + "::appeals-updated");
      })
      .call();
  }

  self.appeals = function( verdictId ) {
    return self.allAppeals[verdictId];
  };

  function upsertAppeal( event ) {
    var appeal = _.assign( event .message,
                           {id: lupapisteApp.models.application.id()});
    var callback = event.callback || _.noop;

    ajax.command( appeal.type === "appealVerdict"
                  ? "upsert-appeal-verdict" : "upsert-appeal",
                  appeal)
      .success( function() {
        fetchAllAppeals();
        callback();
      })
      .error( function( res ) {
        callback( res.text );
      })
      .call();
  }

  hub.subscribe( "application-model-updated", fetchAllAppeals );
  hub.subscribe( self.serviceName + "::upsert-appeal", upsertAppeal  );
};
