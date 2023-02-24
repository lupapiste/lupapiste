LUPAPISTE.SummaryPropertyModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.SummaryBaseModel( params ));

  self.warning = params.warning;
};
