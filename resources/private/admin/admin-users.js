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
               .command("admin-reset-password", {email: email})
               .success(function(e) { callback(e); })
               .call();
           }},
          {name: "toAuthority",
           showFor: function(user) {return (user.enabled && user.role === "applicant") || user.role === "dummy";},
           operation: function(email, callback) {
             ajax
               .command("applicant-to-authority", {email: email})
               .success(function() { callback(true); })
               .call();
           }},
          {name: "retryRakentajafi",
           showFor: function(user) {return user.enabled && user.role === "applicant";},
           operation: function(email, callback) {
             ajax
               .command("retry-rakentajafi", {email: email})
               .success(function() { callback(true); })
               .call();
           }}]
  };

  hub.onPageLoad("users", function() {
    if (!usersList) {
      usersList = users.create($("#users .fancy-users-table"), usersTableConfig);
    }
  });


  //
  // For adding authotity admins
  //

  function AuthorityAdminUsers() {
    var self = this;

    self.organizationCode = ko.observable();
    self.username = ko.observable();
    self.phase = ko.observable(0);
    self.firstName = ko.observable();
    self.lastName = ko.observable();
    self.userDetailsOk = ko.computed(function() {
        var firstNameOk = self.firstName();
        var lastNameOk = self.lastName();
        var organizationCodeOk = self.organizationCode();
        var usernameOk = self.username();
        return organizationCodeOk && usernameOk && firstNameOk && lastNameOk;});

    self.searching = ko.observable();
    self.userAdded = ko.observable();

    self.createdUserlinkFi = ko.observable();
    self.createdUserlinkSv = ko.observable();
    self.createdUserUsername = ko.observable();

    self.clean = function() {
      return self
        .phase(1)
        .organizationCode("")
        .username("")
        .firstName("")
        .lastName("")
        .searching(false)
        .userAdded(false)
        .createdUserlinkFi("")
        .createdUserlinkSv("")
        .createdUserUsername("");
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
        .command("create-user",
                 {email: self.username(),
                  role: "authorityAdmin",
                  organization: self.organizationCode(),
                  enabled: "true",
                  firstName: self.firstName(),
                  lastName: self.lastName()})
        .pending(self.searching)
        .success(function(r) {
          self.createdUserUsername(r.user.username);
          self.createdUserlinkFi(r.linkFi);
          self.createdUserlinkSv(r.linkSv);
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
