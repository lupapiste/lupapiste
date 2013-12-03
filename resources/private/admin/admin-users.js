;(function() {
  "use strict";

  var usersList = null;
  var usersTableConfig = {
    ops: [{name: "enable",
           showFor: function(user) { return !user.enabled; },
           operation: function(email, callback) {
             ajax
               .command("set-user-enabled", {email: email, enabled: true})
               .success(function() { callback(true); })
               .call();
           }},
          {name: "disable",
           showFor: function(user) { return user.enabled; },
           operation: function(email, callback) {
             ajax
               .command("set-user-enabled", {email: email, enabled: false})
               .success(function() { callback(true); })
               .call();
           }},
          {name: "resetPassword",
           showFor: function(user) { return user.enabled; },
           operation: function(email, callback) {
             ajax
               .command("reset-password", {email: email})
               .success(function() { callback(true); })
               .call();
           }}]
  };

  hub.onPageChange("users", function() {
    if (!usersList) usersList = users.create($("#users .fancy-users-table"), usersTableConfig);
  });


  //
  // For adding authotity admins
  //

  function AuthorityAdminUsers() {
    var self = this;

    self.organization = ko.observable();
    self.phase = ko.observable(0);
    self.organization = ko.observable();
    self.firstName = ko.observable();
    self.lastName = ko.observable();
    self.userDetailsOk = ko.computed(function() {
        var firstNameOk = self.firstName();
        var lastNameOk = self.lastName();
        var organizationOk = self.organization();
        return organizationOk && firstNameOk && lastNameOk;});

    self.searching = ko.observable();
    self.userAdded = ko.observable();

    self.linkFi = ko.observable();
    self.linkSv = ko.observable();

    self.clean = function() {
      return self
        .phase(1)
        .organization("")
        .firstName("")
        .lastName("")
        .searching(false)
        .userAdded(false)
        .linkFi("")
        .linkSv("");
    };

    self.dialog = function() {
      if (!self._dialog) {
        self._dialog = new LUPAPISTE.Modal("ModalDialogMask", "black");
        self._dialog.createMask();
      }
      return self._dialog;
    };

    self.addAdmin = function() {
      self.clean().dialog().open("#add-authority-admin-user-to-organization-dialog");
    };

    self.next = function() {
      self.searching(true).phase(2);
      ajax
        .command("update-user-organization",
                 {operation: "add",
                  organization: self.organization(),
                  email: "lupapiste@" + self.organization() + ".fi",
                  firstName: self.firstName(),
                  lastName: self.lastName()})
        .pending(self.searching)
        .success(function(r) {
          self.linkFi(r["link-fi"]);
          self.linkSv(r["link-sv"]);
          self.userAdded(true);
          usersList.redraw();
        })
        .call();
    };
  }

  var authorityAdminUsers = new AuthorityAdminUsers();

  $(function() {
    $("#addAuthAdmin").applyBindings({
      authorityAdminUsers: authorityAdminUsers
    });
  });

})();
