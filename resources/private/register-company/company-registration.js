(function() {
  "use strict";

  function CompanyRegistration() {
    var self = this;

    this.userNotLoggedIn = ko.pureComputed(function() {
      return !(lupapisteApp.models.currentUser && lupapisteApp.models.currentUser.id());
    });

    this.model = ko.validatedObservable({
      //Account type
      accountType:  ko.observable(undefined),
      // Company:
      name:         ko.observable(undefined).extend({required: true, maxLength: 64}),
      y:            ko.observable("").extend({required: true, y: true}),
      reference:    ko.observable(""),
      address1:     ko.observable(""),
      po:           ko.observable(""),
      zip:          ko.observable("").extend({number: true, maxLength: 5}),
      country:      ko.observable(""),
      ovt:          ko.observable("").extend({ovt: true}),
      pop:          ko.observable(""),
      // Signer:
      firstName:    ko.observable("").extend({required: true}),
      lastName:     ko.observable("").extend({required: true}),
      email:        ko.observable("").extend({required: true, email: true,
                                              usernameAsync: this.userNotLoggedIn})
    });

    this.accountFieldNames = ["accountType"];
    this.companyFieldNames = ["name", "y", "reference", "address1", "po", "zip", "country", "ovt", "pop"];
    this.companyFields = this.companyFieldNames.concat(this.accountFieldNames);
    this.signerFieldNames = ["firstName", "lastName", "email"];

    this.stateInfo  = 0;
    this.stateReady = 1;

    this.processId = ko.observable(null);
    this.pending   = ko.observable(false);
    this.state     = ko.observable(this.stateInfo);

    this.canSubmitInfo  = ko.computed(function() { return this.state() === this.stateInfo && !this.pending() && this.model.isValid(); }, this);
    this.canCancelInfo  = ko.computed(function() { return this.state() === this.stateInfo && !this.pending(); }, this);
    this.canStartSign   = ko.computed(function() { return this.state() === this.stateReady; }, this);
    this.accountSelected = function() {
      var buttonPos = $("#account-type-selection").position();
      $("html, body").animate({
        scrollTop: buttonPos.top
      });
      return true;
    };

    self.submitPressed = ko.observable(false);
    self.companyData = ko.pureComputed(function() {
      return _.pick(ko.toJS(self.model()), self.companyFields);
    });
    self.signerData = ko.pureComputed(function() {
      return _.pick(ko.toJS(self.model(), self.signerFieldNames));
    });

    self.initSignCallback = function(processId) {
      self.processId(processId);
      self.state(self.stateReady);
    };
  }

  CompanyRegistration.prototype.clearModel = function(fieldNames) {
    if (!fieldNames) {
      fieldNames = this.accountFieldNames.concat(this.companyFieldNames).concat(this.signerFieldNames);
    }
    var m = this.model();
    _.each(fieldNames, function(k) { m[k](null); });
  };

  CompanyRegistration.prototype.init = function() {
    if (_.isEmpty(this.model().accountType())) {
      window.location.hash = "!/register-company-account-type";
      return;
    }
    this.clearModel(this.companyFieldNames.concat(this.signerFieldNames));
    // check if user is already logged in
    if (lupapisteApp.models.currentUser && !lupapisteApp.models.currentUser.company.id()) {
      this.model().firstName(lupapisteApp.models.currentUser.firstName());
      this.model().lastName(lupapisteApp.models.currentUser.lastName());
      this.model().email(lupapisteApp.models.currentUser.email());
    }
    return this.processId(null).pending(false).state(this.stateInfo);
  };

  CompanyRegistration.prototype.submitInfo = function() {
    var company = _.reduce(this.companyFieldNames.concat(this.accountFieldNames), function(a, k) { a[k] = this[k](); return a; }, {}, this.model()),
        signer = _.reduce(this.signerFieldNames, function(a, k) { a[k] = this[k](); return a; }, {}, this.model()),
        self = this;

    if (!this.userNotLoggedIn()) {
      signer.currentUser = lupapisteApp.models.currentUser.id();
    }

    self.submitPressed(true);

    window.location.hash = "!/register-company-signing";
  };

  CompanyRegistration.prototype.continueToCompanyInfo  = function() {
    window.location.hash = "!/register-company";
  };

  CompanyRegistration.prototype.cancelInfo = function() {
    this.clearModel();
    window.location.hash = "!/register";
  };

  CompanyRegistration.prototype.cancelSign = function() {
    ajax
      .command("cancel-sign", {processId: this.processId()})
      .call();
    this.clearModel();
    window.location.hash = "!/login";
  };

  var companyRegistration = new CompanyRegistration();

  hub.onPageLoad("register-company", companyRegistration.init.bind(companyRegistration));

  hub.onPageLoad("register-company-signing", function() {
    if (_.isEmpty(companyRegistration.model().accountType())) {
      window.location.hash = "!/register-company-account-type";
    }
  });

  $(function() {
    $("#register-company-account-type").applyBindings(companyRegistration);
    $("#register-company").applyBindings(companyRegistration);
    $("#register-company-signing").applyBindings(companyRegistration);
    $("#register-company-success").applyBindings({});
    $("#register-company-existing-user-success").applyBindings({});
    $("#register-company-fail").applyBindings({});
  });

})();
