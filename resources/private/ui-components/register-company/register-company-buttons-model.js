// Container for company registration "wizard" buttons.
LUPAPISTE.RegisterCompanyButtonsModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.companyRegistrationService;

  self.waiting = service.pending;
  self.cancelClick = service.cancel;

  self.showContinue = self.disposedComputed( function() {
    return _.isFunction( service.currentConfig().continueEnable );
  });

  self.continueEnable = self.disposedComputed( function() {
    return Boolean( self.showContinue() &&  service.currentConfig().continueEnable() );
  });

  self.continueClick = function() {
    service.currentConfig().continueClick();
  };
};
