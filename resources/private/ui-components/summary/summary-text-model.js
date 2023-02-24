LUPAPISTE.SummaryTextModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.SummaryBaseModel( params ) );

  self.lLabel = params.ltext;
  self.text = params.text;
  self.spanCss = params.spanCss;
};
