LUPAPISTE.SummaryBaseModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.application = params.application;
  self.editing = params.editing;
  self.uniqId = _.uniqueId( "summary-" );

};
