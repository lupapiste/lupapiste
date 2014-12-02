;(function() {
  "use strict";

  var required = {required: true};
  var notRequired = {required: false};

  function CreateCompanyModel() {
    var self = this;
    var fieldNames = ["name", "y", "address1", "address2", "po", "zip", "email"];

    self.model = ko.validatedObservable({
      // Company:
      name:         ko.observable().extend(required),
      y:            ko.observable().extend(required).extend({y: true}),
      address1:     ko.observable().extend(notRequired),
      address2:     ko.observable().extend(notRequired),
      po:           ko.observable().extend(notRequired),
      zip:          ko.observable().extend(notRequired),
      // Signer:
      email:        ko.observable().extend(required).extend({email: true})
    });

    self.reset = function() {
      _.each(fieldNames, function(k) {
        self.model()[k](null);
      });
    };
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

    self.create = function() {
      createCompanyModel.reset();
      LUPAPISTE.ModalDialog.open("#dialog-create-company");
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
