LUPAPISTE.MattiAdminReviewsModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var CHUNKSIZE = 2;
  var GIVEUP    = 5;

  // For some reason the parameter is observable.
  var orgId =  ko.unwrap( params.organizationId ) ;

  self.batchrunStart = ko.observable();
  self.batchrunEnd = ko.observable();
  self.batchrunIds = ko.observableArray();
  self.batchrunWait = ko.observable();
  self.fetchWait = ko.observable();
  self.batchrunResults = ko.observableArray();
  self.batchrunTargets = ko.observableArray();

  self.batchrunCanFetch = self.disposedComputed( function() {
    return !(s.isBlank( self.batchrunStart())
             || s.isBlank( self.batchrunEnd()))
      && !self.batchrunWait();
  });

  self.clearLog = function() {
    self.batchrunResults.removeAll();
  };

  self.batchrunFetchTargets = function() {
    self.batchrunTargets.removeAll();
    self.batchrunIds.removeAll();
    self.clearLog();
    ajax.query( "matti-review-batchrun-targets", {organizationId: orgId,
                                                  startDate: self.batchrunStart(),
                                                  endDate: self.batchrunEnd()})
      .pending( self.batchrunWait )
      .processing( self.batchrunWait )
      .success( function( res ) {
        self.batchrunTargets( res.applicationIds );
      })
      .call();
  };

  self.batchrunTargetsLabel = self.disposedComputed( function() {
    var selected = _.size( self.batchrunIds() );
    return loc( "matti.review-targets", _.size( self.batchrunTargets()))
      + " "
      + (selected ? loc( "matti.targets-selected", selected) : "");
  });

  function runLap( allIds, attempts ) {
    var ids = _.take( allIds, CHUNKSIZE );
    if( _.size( ids ) ) {
      ajax.command( "matti-review-batchrun",
                    {organizationId: orgId,
                     ids: ids})
        .success( function( res ) {
          self.batchrunResults( _.concat( self.batchrunResults(), res.results ));
          self.batchrunTargets.removeAll( ids );
          runLap( _.drop( allIds, CHUNKSIZE), 0);
        })
        .error( function( res ) {
          self.batchrunWait( false );
          util.showSavedIndicator( res );
        })
        .complete( function( jqXHR, textStatus ) {
          if( textStatus === "timeout" ) {
            if( attempts < GIVEUP ) {
              runLap( allIds, ++attempts );
            } else {
              self.batchrunWait( false );
              hub.send( "indicator", {style: "negative", message: "matti.review-fail"});
            }
          }
        })
        .call();
    } else {
      self.batchrunWait( false );
    }
  }

  self.batchrunRun = function() {
    self.batchrunWait( true );
    runLap( self.batchrunIds(), 0 );
  };

  self.batchrunLog = self.disposedComputed( function() {
    return _( self.batchrunResults() )
      .map( function( r ) {
        return sprintf( "%-20s [%s]:  %s (%s)",
                        r.applicationId, r.taskId, r.taskName, r.result );
      })
      .join( "\n");
  });

};
