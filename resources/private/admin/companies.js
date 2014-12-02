;(function() {
  "use strict";

  function CreateCompanyModel() {
    self.name = ko.observable("");

    self.reset = function() {

    }
  }
  var createCompanyModel = new CreateCompanyModel();

  function CompaniesModel() {
    var self = this;

    self.companies = ko.observableArray([]);
    self.pending = ko.observable();

    self.load = function() {
      ajax
        .query("companies")
        .pending(self.pending)
        .success(function(d) {
          self.companies(_.sortBy(d.companies, "name"));
        })
        .call();
    };
  }

  var companiesModel = new CompaniesModel();

  hub.onPageChange("companies", companiesModel.load);

  $(function() {
    $("#companies").applyBindings({
      companiesModel: companiesModel,
      createCompanyModel: createCompanyModel
    });
  });

})();
