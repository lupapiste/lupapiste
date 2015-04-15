LUPAPISTE.CompanySelectorModel = function(params) {
  "use strict";

  function mapCompany(company) {
    company.displayName = ko.pureComputed(function() {
      return ko.unwrap(company.name) + " (" + ko.unwrap(company.y) + ")";
    });
    return company;
  }

  var self = this;

  self.pending = ko.observable();
  self.selected = ko.observable();

  self.companies = ko.pureComputed(function() {
    return _(lupapisteApp.models.application.roles())
      .filter(function(r) {
        return ko.unwrap(r.type) === "company";
      })
      .map(mapCompany)
      .value();
  });
};
