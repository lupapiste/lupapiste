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
    return self.selectedAccount().price; // price is actually localized text (see service)
  });

  self.normalPriceText = self.disposedPureComputed(function() {
    var acc = self.selectedAccount();
    return (acc.isYearly ? acc.normalYearPrice : acc.priceRaw.monthly) + " \u20AC";
  });

  self.yearlyPriceReductionText = self.disposedPureComputed(function() {
    var acc = self.selectedAccount();
    return "- " + (acc.normalYearPrice - acc.priceRaw.yearly) + " \u20AC";
  });

};
