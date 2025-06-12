LUPAPISTE.SummaryTosFunctionModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.SummaryBaseModel( params ));

  var app = params.application;

  self.selected = app.tosFunction;
  self.required = params.required;
  self.tosFunctions = params.tosFunctions;

  self.text = self.disposedPureComputed( function() {
    return _.get( _.find( self.tosFunctions, {code: self.selected()}),
                  "name",
                  loc( "a11y.summary.missing"));
  });
};
