LUPAPISTE.CompanySelectorModel = function(params) {
  "use strict";

  var self = this;

  self.selected = ko.observable();

  function mapCompany(company) {
    company.displayName = ko.pureComputed(function() {
      return ko.unwrap(company.name) + " (" + ko.unwrap(company.y) + ")";
    });
    company.disable = ko.observable(_.has(company, "invite"));
    return company;
  }

  self.companies = ko.pureComputed(function() {
    return _(lupapisteApp.models.application.roles())
      .filter(function(r) {
        return ko.unwrap(r.type) === "company";
      })
      .map(mapCompany)
      .value();
  });

  self.setOptionDisable = function(option, item) {
    if (!item) {
      return;
    }
    ko.applyBindingsToNode(option, {disable: item.disable}, item);
  };

  self.selected.subscribe(function(id) {
    console.log("selected", id);
  });
};
