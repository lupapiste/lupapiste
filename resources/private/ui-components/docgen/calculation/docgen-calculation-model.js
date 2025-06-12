LUPAPISTE.DocgenCalculationModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel( params ));

  var service = lupapisteApp.services.documentDataService;

  self.testId = _.join( params.path, "-");

  var rowModels = _.map( params.schema.columns, function( col ) {
    return service.getInDocument( params.documentId,
                                  _.concat( _.dropRight( params.path, 1 ),
                                            [col])).model;
  });

  self.calculationResult = self.disposedPureComputed( function() {
    return _.sumBy( rowModels, function( model ) {
      return util.parseFloat( _.trim( model()) || "0" );
    } );
  });

  self.showError = self.disposedPureComputed( function() {
    return _.isNaN( self.calculationResult());
  });
};
