LUPAPISTE.CampaignService = function() {
  "use strict";
  var self = this;

  self.code = ko.observable();
  self.campaign = ko.observable({});
  self.error = ko.observable();

  var moments = {};

  function finnishFormat( m ) {
    return m.format( "D.M.YYYY");
  }

  function processDate( campaign, path ) {
    var m = moment( campaign[path], "YYYY-M-D", true );
    moments[path] = m;
    campaign[path] = finnishFormat( m );
  }

  function processCampaign( campaign ) {
    _.each( ["starts", "ends", "lastDiscountDate"],
            _.partial( processDate, campaign ));
    return campaign;
  }

  ko.computed( function() {
    self.error( "");
    self.campaign({});
    if( _.trim( self.code())) {
      ajax.query( "campaign", {code: self.code()})
      .success( function( res ) {
        processCampaign( res.campaign );
        self.campaign( res.campaign );
      })
      .error( function( res ) {
        self.error( res.text );
      })
      .call();
    }
  });

  self.campaignTexts = ko.computed( function() {
    return self.campaign().code
         ? {title: "company.campaign.title",
            subtitle: loc( "company.campaign.subtitle",
                           self.campaign().lastDiscountDate),
           lastDiscount: loc( "company.campaign.until",
                            self.campaign().lastDiscountDate)}
         : null;
  });

  self.campaignPrice = function( id ) {
    return self.campaign().code
         ? loc( "register.company.price", self.campaign()[id])
         : null;
  };

  self.campaignSmallPrint = function( id ) {
    var account = _.find(LUPAPISTE.config.accountTypes, {name: id});
    return loc( "company.campaign.small-print",
                finnishFormat( moment( moments.lastDiscountDate)
                               .add( 1, "days")),
                loc("register.company.price", account.price),
                self.campaign().lastDiscountDate);

  };

  // -------------------------
  // Admin admin
  // -------------------------

  self.campaigns = ko.observableArray();
  self.waiting = ko.observable();

  self.queryCampaigns = function() {
    ajax.query( "campaigns")
    .pending( self.waiting )
    .success( function( res ) {
      self.campaigns( _.map( res.campaigns, processCampaign ));
    })
    .call();
  };

  self.deleteCampaign = function( code ) {
    ajax.command( "delete-campaign", {code: code})
    .success( self.queryCampaigns )
    .call();
  };

  // Hardcoded campaign code
  if( LUPAPISTE.config.campaignCode ) {
    self.code( LUPAPISTE.config.campaignCode );
  }
};
