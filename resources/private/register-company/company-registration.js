(function() {
  "use strict";

  function CompanyRegistration() {
    var self = this;

    self.userLoggedIn = ko.pureComputed(function() {
      return lupapisteApp.models.currentUser && lupapisteApp.models.currentUser.id();
    });

    self.userNotLoggedIn = ko.pureComputed(function() {
      return !self.userLoggedIn();
    });

    self.model = ko.validatedObservable({
      //Account type
      accountType:  ko.observable(undefined),
      // Company:
      name:         ko.observable(undefined).extend({required: true, maxLength: 64}),
      y:            ko.observable("").extend({required: true, y: true}),
      reference:    ko.observable(""),
      address1:     ko.observable("").extend({required: true}),
      po:           ko.observable("").extend({required: true}),
      zip:          ko.observable("").extend({required: true, number: true, maxLength: 5, minLength: 5}),
      country:      ko.observable(""),
      netbill:      ko.observable(""),
      ovt:          ko.observable("").extend({ovt: true}),
      pop:          ko.observable(""),
      // Signer:
      firstName:    ko.observable("").extend({required: true}),
      lastName:     ko.observable("").extend({required: true}),
      email:        ko.observable("").extend({required: true, email: true,
                                              usernameAsync: self.userNotLoggedIn}),
      personId:     ko.observable("").extend({conditional_required: self.userNotLoggedIn, personId: true})
    });

    self.accountFieldNames = ["accountType"];
    self.companyFieldNames = ["name", "y", "reference", "address1", "po", "zip", "country", "netbill", "ovt", "pop"];
    self.companyFields = self.companyFieldNames.concat(self.accountFieldNames);
    self.signerFieldNames = ["firstName", "lastName", "email", "personId"];

    self.stateInfo  = 0;
    self.stateReady = 1;

    self.processId = ko.observable(null);
    self.pending   = ko.observable(false);
    self.state     = ko.observable(self.stateInfo);

    self.canSubmitInfo  = ko.computed(function() { return self.state() === self.stateInfo && !self.pending() && self.model.isValid(); });
    self.canCancelInfo  = ko.computed(function() { return self.state() === self.stateInfo && !self.pending(); });
    self.canStartSign   = ko.computed(function() { return self.state() === self.stateReady; });
    self.accountSelected = function() {
      var buttonPos = $("#account-type-selection").position();
      $("html, body").animate({
        scrollTop: buttonPos.top
      });
      return true;
    };

    self.initSignCallback = function(processId) {
      self.processId(processId);
      self.state(self.stateReady);
    };

    self.termsAccepted = ko.observable(false);
    self.termsDocumentLink = ko.pureComputed(function() {
      return "/api/sign/document/" + self.processId();
    });
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
      pageutil.openPage("register-company-account-type");
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
    var self = this;
    var company = _.pick(ko.toJS(self.model()), self.companyFields);
    var signer = _.pick(ko.toJS(self.model()), self.signerFieldNames);

    self.termsAccepted(false);

    if (!this.userNotLoggedIn()) {
      signer.currentUser = lupapisteApp.models.currentUser.id();
    }

    hub.send("company-info-submitted", {company: company, signer: signer});

    pageutil.openPage("register-company-signing");
  };

  CompanyRegistration.prototype.continueToCompanyInfo  = function() {
    pageutil.openPage("register-company");
  };

  CompanyRegistration.prototype.cancelInfo = function() {
    this.clearModel();
    pageutil.openPage("register");
  };

  CompanyRegistration.prototype.cancelSign = function() {
    ajax
      .command("cancel-sign", {processId: this.processId()})
      .call();
    this.clearModel();
    pageutil.openPage("register");
  };

  var companyRegistration = new CompanyRegistration();

  hub.onPageLoad("register-company", companyRegistration.init.bind(companyRegistration));

  hub.onPageLoad("register-company-signing", function() {
    if (_.isEmpty(companyRegistration.model().accountType())) {
      pageutil.openPage("register-company-account-type");
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
