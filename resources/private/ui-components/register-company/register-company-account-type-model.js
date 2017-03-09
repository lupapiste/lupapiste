// Company account type selection.
LUPAPISTE.RegisterCompanyAccountTypeModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.companyRegistrationService;
  var campService = lupapisteApp.services.campaignService;

  self.accountTypes = service.accountTypes;

  self.selected = service.registration.accountType;

  self.selectedCss = function( id ) {
    var isSelected = id === self.selected();
    return {selected: isSelected,
            "other-selected": self.selected() && !isSelected,
            "not-selected": !isSelected};
  };

  self.price = function( data ) {
    return campService.campaignPrice( data.id)
        || data.price;
  };

  self.campaign = campService.campaign;
  self.lastDiscount = campService.campaignTexts().lastDiscount;
  self.campaignSmallPrint = campService.campaignSmallPrint;
};
