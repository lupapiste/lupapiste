// Signing step UI for the company registration.
LUPAPISTE.RegisterCompanySignModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());
  var service = lupapisteApp.services.companyRegistrationService;

  self.agreed = ko.observable();
  self.form = service.signResults;
  self.contractUrl = self.disposedPureComputed( function() {
    return sprintf( "/api/sign/document/%s", util.getIn( self.form, ["processId"]) );
  });
  self.cancelClick = service.cancel;
};
