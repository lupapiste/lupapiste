LUPAPISTE.RegisterCompanySignModel = function() {
  "use strict";
  var self = this;

  var service = lupapisteApp.services.companyRegistrationService;

  self.agreed = ko.observable();
  self.contractUrl = service.signResults()["process-id"];
  self.cancelClick = service.cancel;
  self.signClick = _.noop;

};
