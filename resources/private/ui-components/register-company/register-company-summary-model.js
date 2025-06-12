// Container component for company registration summary page.
// The page shows summary of selected account and billing types, and the total amount.
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

  function sixMonthsPrice() {
    var acc = self.selectedAccount();
    return acc.isYearly ? null : 6 * acc.priceRaw.monthly;
  }

  self.totalPriceText = self.disposedPureComputed(function() {
    return loc("register.company.price",
               sixMonthsPrice() || self.selectedAccount().price);
  });

  self.normalPriceText = self.disposedPureComputed(function() {
    var acc = self.selectedAccount();
    return (sixMonthsPrice() || acc.normalYearPrice) + " €";
  });

  self.yearlyPriceReductionText = self.disposedPureComputed(function() {
    var acc = self.selectedAccount();
    return "- " + (acc.normalYearPrice - acc.priceRaw.yearly) + " €";
  });

};
