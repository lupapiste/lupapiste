// Add new campaign.
// Params:
// showEditor: Visiblity observable.
LUPAPISTE.CampaignEditorModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.campaignService;

  self.showEditor = params.showEditor;

  self.code = ko.observable();
  self.starts = ko.observable();
  self.ends = ko.observable();
  self.lastDiscountDate = ko.observable();
  self.account5 = ko.observable();
  self.account15 = ko.observable();
  self.account30 = ko.observable();
  self.waiting = service.waiting;
  self.error = ko.observable();

  function isoDate( finnishDate ) {
    return moment( finnishDate, "D.M.YYYY", true)
           .format( "YYYY-MM-DD");
  }

  function isInt( s ) {
    return _.isInteger( util.parseFloat( s ));
  }

  self.okEnabled = self.disposedComputed( function() {
    return _.trim( self.code() )
    // Dates are checked in backend
        && _.trim( self.starts())
        && _.trim( self.ends())
        && _.trim( self.lastDiscountDate())
        && isInt( self.account5() )
        && isInt( self.account15() )
        && isInt( self.account30() );
  });


  self.save = function() {
    self.error( "" );
    ajax.command( "add-campaign",
                  {code: self.code(),
                   starts: isoDate(self.starts()),
                   ends: isoDate(self.ends()),
                   lastDiscountDate: isoDate(self.lastDiscountDate()),
                   account5: _.toInteger(self.account5()),
                   account15: _.toInteger(self.account15()),
                   account30: _.toInteger(self.account30())} )
    .pending( self.waiting )
    .success( function() {
      service.queryCampaigns();
      self.showEditor( false );
    })
    .error( function( res ) {
      self.error( res.text );
    })
    .call();
  };
};
