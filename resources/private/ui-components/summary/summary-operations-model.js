LUPAPISTE.SummaryOperationsModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.SummaryBaseModel( params ));

  self.operations = self.disposedPureComputed( function() {
    return _( params.application.secondaryOperations() )
      .map( function( op ) {
        return op.name();
      })
      .groupBy()
      .map( function( ops ) {
        var count = _.size( ops );
        var opName = loc( "operations." + ops[0] );
        return count > 1
          ? sprintf( "%s \u00d7 %s", count, opName )
          : opName;
      })
      .value();
  });
};
