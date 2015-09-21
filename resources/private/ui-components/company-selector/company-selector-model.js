LUPAPISTE.CompanySelectorModel = function(params) {
  "use strict";

  var self = this;

  self.indicator = ko.observable();
  self.result = ko.observable();
  self.authorization = lupapisteApp.models.applicationAuthModel;

  function mapCompany(company) {
    company.displayName = ko.pureComputed(function() {
      return ko.unwrap(company.name) + " (" + ko.unwrap(company.y) + ")";
    });
    company.disable = ko.observable(_.has(company, "invite"));
    return company;
  }

  function getCompanies() {
    return _(lupapisteApp.models.application.roles())
      .filter(function(r) {
        return ko.unwrap(r.type) === "company";
      })
      .map(mapCompany)
      .value();
  }

  self.companies = ko.observableArray(getCompanies());
  self.selected = ko.observable(_.isEmpty(params.selected) ? undefined : params.selected);

  self.setOptionDisable = function(option, item) {
    if (!item) {
      return;
    }
    ko.applyBindingsToNode(option, {disable: item.disable}, item);
  };

  self.selected.subscribe(function(id) {
    var p = {
      id: lupapisteApp.models.application.id(),
      documentId: params.documentId,
      companyId: id ? id : "",
      path: params.path
    };
    ajax.command("set-company-to-document", p)
    .success(function() {
      function cb() {
        repository.load(lupapisteApp.models.application.id());
      }

      uiComponents.save("update-doc",
                         params.documentId,
                         lupapisteApp.models.application.id(),
                         params.schema.name,
                         params.path.split(".").concat([params.schema.name]),
                         id,
                         self.indicator,
                         self.result,
                         cb);
    })
    .call();
  });
};
