LUPAPISTE.CompanyInviteDialogModel = function(params) {
  "use strict";
  var self = this;

  self.pending = ko.observable(false);
  self.companies  = ko.observableArray();
  self.selected   = ko.observable();

  self.isSubmitEnabled = ko.pureComputed(function() {
    return self.selected() && !self.pending();
  });

  self.isSubmitVisible = true;

  self.submit = function() {
    ajax
      .command("company-invite", {"id": lupapisteApp.models.application.id(), "company-id": self.selected().id})
      .pending(self.pending)
      .success(function() {
        repository.load(lupapisteApp.models.application.id());
        hub.send("close-dialog");
      })
      .call();
  };

  function mapCompany(company) {
    company.label = company.name + ", " + (company.address1 ? company.address1 + " ": "") + (company.po ? company.po : "");
    return company;
  }

  ajax
    .query("companies")
    .pending(self.pending)
    .success(function(r) {
      self.companies(_.map(r.companies, mapCompany));
    })
    .call();

};
