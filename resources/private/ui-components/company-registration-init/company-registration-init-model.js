LUPAPISTE.CompanyRegistrationInitModel = function(params) {
  "use strict";

  var self = this;

  self.customerId = ko.observable();
  self.data = ko.observable();
  self.iv = ko.observable();
  self.returnFailure = ko.observable();
  self.postTo = ko.observable();

  ajax
    .command("init-sign", {company: params.company(), signer: params.signer(), lang: loc.currentLanguage})
    .success(function(resp) {
      self.customerId(resp['customer-id']);
      self.data(resp['data']);
      self.iv(resp['iv']);
      self.returnFailure(resp['failure-url']);
      self.postTo(resp['post-to']);

      params.processId(resp['process-id']);
      params.state(1);
    })
    .call();

  self.startSigning = function() {
    return true;
  };
};