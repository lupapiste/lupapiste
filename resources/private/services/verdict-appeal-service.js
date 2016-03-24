// Service for managing verdict appeals.
LUPAPISTE.VerdictAppealService = function() {
  "use strict";
  var self = this;

  self.serviceName = "verdictAppealService";

  self.allAppeals = {};

  function fetchAllAppeals() {
    if( lupapisteApp.models.applicationAuthModel.ok( "appeals")) {
      ajax.query( "appeals", {id: lupapisteApp.models.application.id()})
        .success( function( res ) {
          self.allAppeals = res.data;
          hub.send( self.serviceName + "::appeals-updated");
        })
        .call();
    }
  }

  function command( name, params, callback ) {
    if( self.editsAllowed ) {
      ajax.command( name,
                    _.assign( params,
                              {id: lupapisteApp.models.application.id()}))
        .success( function() {
          hub.send( "indicator", {style: "positive"});
          fetchAllAppeals();
          (callback || _.noop)();
        })
        .error( function( res ) {
          if( callback ) {
            callback( res.text );
          } else {
            hub.send( "indicator", {style: "negative"});
          }
        })
        .call();
    }
  }

  function upsertAppeal( event ) {
    command( event.message.type === "appealVerdict"
             ? "upsert-appeal-verdict" : "upsert-appeal",
             event.message,
             event.callback );
  }

  function deleteAppeal( event ) {
    var params = _.pick( event.message, ["verdictId", "appealId"]);
    command( event.message.type === "appealVerdict"
             ? "delete-appeal-verdict" : "delete-appeal",
             params );
  }

  // Public interface

  self.appeals = function( verdictId ) {
    return self.allAppeals[verdictId];
  };

  self.editsAllowed = function() {
    // All the commands have the same access levels.
    return lupapisteApp.models.applicationAuthModel.ok( "delete-appeal");
  };

  self.hasAppeals = function() {
    return Boolean( _.size( self.allAppeals ));
  };


  hub.subscribe( "application-model-updated", fetchAllAppeals );
  hub.subscribe( self.serviceName + "::upsert-appeal", upsertAppeal );
  hub.subscribe( self.serviceName + "::delete-appeal", deleteAppeal );
};
