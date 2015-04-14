LUPAPISTE.CompanySelectorModel = function(params) {
  "use strict";

  function mapCompany(company) {
    company.displayName = ko.pureComputed(function() {
      return company.name + ", " + (company.address1 ? company.address1 + " ": "") + (company.po ? company.po : "");
    });
    return company;
  }

  var self = this;

  console.log("CompanySelectorModel", params);

  self.companies = ko.observableArray();
  self.pending = ko.observable();
  self.selected = ko.observable();

  _.defer(function() {
    ajax
      .query("companies")
      .pending(self.pending)
      .success(function(response) {
        self.companies(_.map(response.companies, mapCompany));
      })
      .call();
  });
};
