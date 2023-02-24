// Company account type selection.
LUPAPISTE.RegisterCompanyAccountTypeModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.companyRegistrationService;

  self.accountTypes = service.accountTypes;

  self.selected = service.registration.accountType;

  self.billingType = service.registration.billingType;

  self.selectedStatus = function( id ) {
    if( self.selected() ) {
      // KO ignores false values otherwise.
      return "" + ( id === self.selected() );
    }
  };

  self.price = function( data ) {
    return loc("register.company.price", data.price);
  };

  self.toggleBilling = function(type) { // toggles selected billing type defined in companyRegistrationService
    self.billingType(type);
  };
};
