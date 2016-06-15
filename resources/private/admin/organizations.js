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
          self.organizations(_.sortBy(d.organizations, function(o) { return o.name[loc.getCurrentLanguage()]; }));
        })
        .call();
    };
  }

  var organizationsModel = new OrganizationsModel();

  function LoginAsModel() {
    var self = this;
    self.role = ko.observable("approver");
    self.password = ko.observable("");
    self.organizationId = null;

    self.open = function(organization) {
      self.organizationId = organization.id;
      self.password("");
      LUPAPISTE.ModalDialog.open("#dialog-login-as");
    };

    self.login = function() {
      ajax
        .command("impersonate-authority", {organizationId: self.organizationId, role: self.role(), password: self.password()})
        .success(function() {
          var redirectLocation = self.role() === "authorityAdmin" ? self.role() : "authority";
          window.location.href = "/app/fi/" + _.kebabCase(redirectLocation);
        })
        .call();
      return false;
    };
  }
  var loginAsModel = new LoginAsModel();

  hub.onPageLoad("organizations", organizationsModel.load);

  $(function() {
    $("#organizations").applyBindings({
      "organizationsModel": organizationsModel,
      "loginAsModel": loginAsModel
    });
  });

})();
