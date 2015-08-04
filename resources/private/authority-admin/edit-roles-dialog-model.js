LUPAPISTE.EditRolesDialogModel = function(organization, usersList) {
  "use strict";

  var self = this;

  self.availableRoles = ko.pureComputed(function () {
    return _.filter(LUPAPISTE.config.authorityRoles, function(role) {
      return role.indexOf("tos-") < 0 || (organization.permanentArchiveEnabled && organization.permanentArchiveEnabled());
    });
  });

  self.selectedRoles = ko.observableArray();
  self.email = ko.observable();
  self.userName = ko.observable();
  self.greeting = ko.pureComputed(function () {
    return loc("auth-admin.edit-roles.greeting", self.userName());
  });
  self.isOk = ko.pureComputed(function () {
    return self.selectedRoles().length !== 0;
  });

  self.showDialog = function (data) {
    self.selectedRoles(data.roles[organization.organizationId()]);
    self.userName(data.name);
    self.email(data.email);
    LUPAPISTE.ModalDialog.open("#dialog-edit-roles");
  };

  self.okPressed = function () {
    ajax
      .command("update-user-roles", {email: self.email(), roles: self.selectedRoles()})
      .success(_.partial(hub.send, "redraw-users-list"))
      .error(function(error) {
        if (error.text === "error.user-not-found") {
          notify.error(loc("error.dialog.title"), loc("error.user-not-found"));
        } else {
          notify.error(loc("error.dialog.title"), loc(error.text));
        }
      })
      .call();
  };
}
