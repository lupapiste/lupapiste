// Company account type selection.
LUPAPISTE.RegisterCompanyAccountTypeModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.companyRegistrationService;

  self.accountTypes = service.accountTypes;

  self.selected = service.registration.accountType;

  self.billingType = service.registration.billingType;

  self.selectedCss = function( id ) {
    var isSelected = id === self.selected();
    return {selected: isSelected,
            "other-selected": self.selected() && !isSelected,
            "not-selected": !isSelected};
  };

  self.price = function( data ) {
    return loc("register.company.price", data.price);
  };

  self.toggleBilling = function(type) { // toggles selected billing type defined in companyRegistrationService
    self.billingType(type);
  };
};
