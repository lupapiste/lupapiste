(function() {
  "use strict";

  function CampaignManager() {
    var self = this;

    var service = lupapisteApp.services.campaignService;

    self.showEditor = ko.observable();
    self.addCampaign = function() {
      self.showEditor( !self.showEditor());
    };
    self.deleteCampaign = function( data ) {
      service.deleteCampaign( data.code );
    };

    self.campaigns = service.campaigns;
    // Initial query
    service.queryCampaigns();
  }

  $(function() {
    $("#campaigns").applyBindings( new CampaignManager());
  });

})();
