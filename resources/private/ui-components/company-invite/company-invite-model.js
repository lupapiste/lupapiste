LUPAPISTE.CompanyInviteModel = function(params) {
  "use strict";
  var self = this;

  self.isVisible = ko.pureComputed(function() {
    return lupapisteApp.models.applicationAuthModel.ok("invite-with-role");
  });

  self.inviteCompany = function() {
    self.open();
  };

  // TODO own component after modal dialog component refactor
  self.selector   = "#dialog-invite-company";
  self.pending    = ko.observable();
  self.companies  = ko.observableArray();
  self.selected   = ko.observable();

  self.canSubmit  = ko.computed(function() { return !self.pending() && self.selected(); }, self);

  self.open = function() {
    LUPAPISTE.ModalDialog.open(self.selected(null).load().selector);
  };

  self.load = function() {
    if (_.isEmpty(self.companies())) {
      ajax
        .query("companies")
        .pending(self.pending)
        .success(function(r) {
          self.companies(r.companies);
        })
        .call();
    }
    return self;
  };

  self.companyName = function(company) {
    return company.name + ", " + (company.address1 ? company.address1 + " ": "") + (company.po ? company.po : "");
  };

  self.submit = function() {
    console.log("submit");
    ajax
      .command("company-invite", {"id": lupapisteApp.models.application.id(), "company-id": self.selected().id})
      .pending(self.pending)
      .success(function() {
        repository.load(lupapisteApp.models.application.id()); LUPAPISTE.ModalDialog.close();
      })
      .call();
  };

};
