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
      })
      .call();
  }

  self.appeals = function( verdictId ) {
    return self.allAppeals[verdictId];
  };

  function upsertAppeal( event ) {
    var appeal = event.message;
    appeal.date = moment( appeal.date, "D.M.YYYY", true).unix();
    var isVerdict = appeal.appealType === "appealVerdict";
    var keys = {verdictId: "targetId",
                authors: isVerdict ? "giver" : "appellant",
                date: "made",
                extra: "text"
               };
    if( !isVerdict) {
      keys.appealType = "type";
    }
    var acc = {id: lupapisteApp.models.application.id(), appealId: appeal.appealId};

    ajax.command( isVerdict ? "upsert-appeal-verdict" : "upsert-appeal",
                  _.reduce( keys, function( acc, v, k ) {
                    acc[v] = appeal[k];
                    return acc;
                  }, acc))
      .success( function() {
        fetchAllAppeals();
        event.callback();
      })
      .error( function( res ) {
        event.callback( res.text );
      })
      .call();
  }

  hub.subscribe( "application-model-updated", fetchAllAppeals );
  hub.subscribe( self.serviceName + "::upsertAppeal", upsertAppeal  );



};
