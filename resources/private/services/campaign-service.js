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

  ko.computed( function() {
    self.error( "");
    self.campaign({});
    if( _.trim( self.code())) {
      ajax.query( "campaign", {code: self.code()})
      .success( function( res ) {
        _.each( ["starts", "ends", "lastDiscountDate"],
              _.partial( processDate, res.campaign ));
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
         : {};
  });

  self.campaignPrice = function( id ) {
    return self.campaign().code
         ? loc( "register.company.price", self.campaign()[id])
         : null;
  };

  self.campaignSmallPrint = function( id ) {
    return loc( "company.campaign.small-print",
                finnishFormat( moment( moments.lastDiscountDate)
                               .add( 1, "days")),
                _.find( LUPAPISTE.config.accountTypes,
                        {name: id}).price,
                self.campaign().lastDiscountDate);

  };

  if( features.enabled( "company-campaign")) {
    self.code( "huhtikuu2017");
  }
};
