LUPAPISTE.CompanyRegistrationInitModel = function(params) {
  "use strict";

  var self = this;

  self.customerId = ko.observable();
  self.data = ko.observable();
  self.iv = ko.observable();
  self.returnFailure = ko.observable();
  self.postTo = ko.observable();
  self.buttonEnabled = params.buttonEnabled;

  hub.subscribe('company-info-submitted', function(data) {
    ajax
      .command("init-sign", {company: data.company, signer: data.signer, lang: loc.currentLanguage})
      .success(function(resp) {
        self.customerId(resp['customer-id']);
        self.data(resp['data']);
        self.iv(resp['iv']);
        self.returnFailure(resp['failure-url']);
        self.postTo(resp['post-to']);

        params.processIdCallback(resp['process-id']);
      })
      .call();
  });
};
