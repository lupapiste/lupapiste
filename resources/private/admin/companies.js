;(function() {
  "use strict";

  var required = {required: true};
  var notRequired = {required: false};

  function CreateCompanyModel() {
    var self = this;
    var fieldNames = ["name", "y", "address1", "po", "zip", "email"];

    self.errorMessage = ko.observable();

    self.model = ko.validatedObservable({
      // Company:
      name:         ko.observable().extend(required),
      y:            ko.observable().extend(required).extend({y: true}),
      address1:     ko.observable().extend(notRequired),
      po:           ko.observable().extend(notRequired),
      zip:          ko.observable().extend(notRequired),
      // Signer:
      email:        ko.observable().extend(required).extend({email: true})
    });

    self.reset = function() {
      self.errorMessage();
      _.each(fieldNames, function(k) {
        self.model()[k](null);
      });
    };

    self.save = function() {
      ajax.command("create-company", _.reduce(fieldNames, function(d, k) {
          d[k] = self.model()[k](); return d;}, {}))
        .success(function() {
          hub.send("company-created");
          LUPAPISTE.ModalDialog.close();
        })
        .error(function(e) {
          self.errorMessage(e.text);
        })
        .call();
    };
  }
  var createCompanyModel = new CreateCompanyModel();

  function getUserLimit(c) {
    if (c.accountType === "custom") {
      return c.customAccountLimit ? c.customAccountLimit : 0;
    } else {
      return new RegExp("account(\\d+)").exec(c.accountType)[1]; // get the number after str 'account'
    }
  }

  function CompaniesModel() {
    var self = this;

    self.companies = ko.observableArray([]);
    self.pending = ko.observable();

    self.editDialog = function(company) {
     hub.send("show-dialog", {title: "Muokkaa yritysta",
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

    self.create = function() {
      createCompanyModel.reset();
      LUPAPISTE.ModalDialog.open("#dialog-create-company");
    };
  }

  var companiesModel = new CompaniesModel();

  hub.subscribe("company-created", companiesModel.load);
  hub.onPageLoad("companies", companiesModel.load);

  $(function() {
    $("#companies").applyBindings({
      companiesModel: companiesModel,
      createCompanyModel: createCompanyModel
    });
  });

})();
