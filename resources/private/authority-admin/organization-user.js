LUPAPISTE.OrganizationUserModel = function(organization) {
  "use strict";

  var self = this;

  self.phase = ko.observable(0);
  self.email = ko.observable();
  self.firstName = ko.observable();
  self.lastName = ko.observable();
  self.userRoles = ko.observableArray();
  self.userDetailsOk = ko.computed(function() {
      var firstNameOk = self.firstName();
      var lastNameOk = self.lastName();
      var rolesOk = self.userRoles().length >= 1;
      return /.+@.+/.test(self.email()) && firstNameOk && lastNameOk && rolesOk;});

  self.searching = ko.observable();
  self.userAdded = ko.observable();
  self.invitationSent = ko.observable();

  self.availableUserRoles = organization.allowedRoles;

  self.clean = function() {
    return self
      .phase(1)
      .email("")
      .firstName("")
      .lastName("")
      .searching(false)
      .userAdded(false)
      .invitationSent(false)
      .userRoles(["authority", "approver"]);
  };

  self.dialog = function() {
    if (!self._dialog) {
      self._dialog = new LUPAPISTE.Modal("ModalDialogMask", "black");
      self._dialog.createMask();
    }
    return self._dialog;
  };

  self.addUserToOrganization = function() {
    self.clean().dialog().open("#add-user-to-organization-dialog");
  };

  self.next = function() {
    self.searching(true).phase(2);
    ajax
      .command("upsert-organization-user",
               {organizationId: organization.organizationId(),
                email: self.email(),
                firstName: self.firstName(),
                lastName: self.lastName(),
                roles: self.userRoles()})
      .pending(self.searching)
      .success(function(r) {
        if (r.operation === "invited") {
          self.invitationSent(true);
        } else {
          self.userAdded(true);
        }

        hub.send("redraw-users-list");
      })
      .error(function(e) {
        if (e.text === "error.user-not-found") {
          notify.error(loc("error.dialog.title"), loc("error.not-authority"));
        } else {
          notify.ajaxError(e);
        }
      })
      .call();
  };
};
