LUPAPISTE.CompanyRegistrationInitModel = function(params) {
  "use strict";

  var self = this;

  self.customerId = ko.observable();
  self.data = ko.observable();
  self.iv = ko.observable();
  self.returnFailure = ko.observable();
  self.postTo = ko.observable();
  self.buttonEnabled = params.buttonEnabled;

  ajax
    .command("init-sign", {company: params.company(), signer: params.signer(), lang: loc.currentLanguage})
    .success(function(resp) {
      self.customerId(resp['customer-id']);
      self.data(resp['data']);
      self.iv(resp['iv']);
      self.returnFailure(resp['failure-url']);
      self.postTo(resp['post-to']);

      params.processIdCallback(resp['process-id']);
    })
    .call();
};