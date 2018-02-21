LUPAPISTE.RegisterCompanySummaryModel = function() {
  "use strict";
  var self = this;
  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.companyRegistrationService;

  self.selectedAccount = service.selectedAccount;

  self.billingType = service.registration.billingType;

  self.summaryText = self.disposedPureComputed(function() {
    return self.selectedAccount().title
           + " - "
           + _.lowerCase(loc("register.company.billing." + self.billingType() + ".title"));
  });

  self.totalPriceText = self.disposedPureComputed(function() {
    return self.selectedAccount().price;
  });

};
