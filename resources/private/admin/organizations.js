;(function() {
  "use strict";

  function OrganizationsModel() {
    var self = this;

    self.organizations = ko.observableArray([]);
    self.pending = ko.observable();

    self.load = function() {
      ajax
        .query("organizations")
        .pending(self.pending)
        .success(function(d) {
          self.organizations(_.sortBy(d.organizations, function(d) { return d.name[loc.getCurrentLanguage()]; }));
        })
        .call();
    };
  }

  var organizationsModel = new OrganizationsModel();

  function EditOrganizationModel() {
    var self = this;
    self.dialogSelector = "#dialog-edit-organization";
    self.errorMessage = ko.observable(null);

    // Model

    self.organizationScope = null;
    self.applicationEnabled = ko.observable(false);
    self.inforequestEnabled = ko.observable(false);
    self.openInforequestEnabled = ko.observable(false);
    self.openInforequestEmail = ko.observable("");
    self.processing = ko.observable();
    self.pending = ko.observable();

    self.reset = function(organizationScope) {
      self.organizationScope = organizationScope;
      self.applicationEnabled(organizationScope['new-application-enabled'] || false);
      self.inforequestEnabled(organizationScope['inforequest-enabled'] || false);
      self.openInforequestEnabled(organizationScope['open-inforequest'] || false);
      self.openInforequestEmail(organizationScope['open-inforequest-email'] || "");
      self.processing(false);
      self.pending(false);
    };

    self.ok = ko.computed(function() {
      return true;
    });

    // Open the dialog

    self.open = function(organizationScope) {
      self.reset(organizationScope);
      LUPAPISTE.ModalDialog.open(self.dialogSelector);
    };

    self.onSuccess = function() {
      self.errorMessage(null);
      LUPAPISTE.ModalDialog.close();
      organizationsModel.load();
    };

    self.onError = function(resp) {
      self.errorMessage(resp.text);
    };

    self.updateOrganization = function() {
      var data = {permitType: self.organizationScope.permitType,
                  municipality: self.organizationScope.municipality,
                  inforequestEnabled: self.inforequestEnabled(),
                  applicationEnabled: self.applicationEnabled(),
                  openInforequestEnabled: self.openInforequestEnabled(),
                  openInforequestEmail: self.openInforequestEmail()};
      ajax.command("update-organization", data)
        .processing(self.processing)
        .pending(self.pending)
        .success(self.onSuccess)
        .error(self.onError)
        .call();
      return false;
    };

  }

  var editOrganizationModel = new EditOrganizationModel();

  function LoginAsModel() {
    var self = this;
    self.password = ko.observable("");
    self.organizationId = null;

    self.open = function(organization) {
      self.organizationId = organization.id;
      self.password("");
      LUPAPISTE.ModalDialog.open("#dialog-login-as");
    };

    self.login = function() {
      ajax
        .command("impersonate-authority", {organizationId: self.organizationId, password: self.password()})
        .success(function(d) {
          window.location.href = "/app/fi/authority";
        })
        .call();
      return false;
    };
  }
  var loginAsModel = new LoginAsModel();

  hub.onPageChange("organizations", organizationsModel.load);

  $(function() {
    $("#organizations").applyBindings({
      "organizationsModel": organizationsModel,
      "editOrganizationModel": editOrganizationModel,
      "loginAsModel": loginAsModel
    });
  });

})();
