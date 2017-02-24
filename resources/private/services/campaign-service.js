LUPAPISTE.CampaignService = function() {
  "use strict";
  var self = this;

  self.code = ko.observable();
  self.campaign = ko.observable({});
  self.error = ko.observable();

  ko.computed( function() {
    self.error( "");
    self.campaign({});
    if( _.trim( self.code())) {
      ajax.query( "campaign", {code: self.code()})
      .success( function( res ) {
        self.campaign( res.campaign );
      })
      .error( function( res ) {
        self.error( res.text );
      })
      .call();
    }
  });
};
