;(function() {
  "use strict";

  var required = {required: true};
  var notRequired = {required: false};

  function getUserLimit(c) {
    if (c.accountType === "custom") {
      return c.customAccountLimit ? c.customAccountLimit : 0;
    } else {
      return _.findWhere(LUPAPISTE.config.accountTypes, {name: c.accountType}).limit;
    }
  }

  function CompaniesModel() {
    var self = this;

    self.companies = ko.observableArray([]);
    self.pending = ko.observable();

    self.editDialog = function(company) {
     hub.send("show-dialog", {title: "Muokkaa yritysta",
                              size: "medium",
                              component: "company-edit",
                              componentParams: {company: company}});
    };

    self.load = function() {
      ajax
        .query("companies")
        .pending(self.pending)
        .success(function(d) {
          self.companies(_(d.companies).map(function(c) {
            return _.assign(c, {userLimit: getUserLimit(c),
                                openDialog: self.editDialog});
          }).sortBy("name").value());
        })
        .call();
    };

  }

  var companiesModel = new CompaniesModel();

  hub.subscribe("company-created", companiesModel.load);
  hub.subscribe("company-updated", companiesModel.load);
  hub.onPageLoad("companies", companiesModel.load);

  $(function() {
    $("#companies").applyBindings({
      companiesModel: companiesModel
    });
  });

})();
