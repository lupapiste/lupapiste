;(function() {
  "use strict";

  var usersList = null;
  var usersTableConfig = {
    ops: [{name: "enable",
           button: "secondary",
           showFor: function(user) { return !user.enabled; },
           operation: function(email, callback) {
             ajax
               .command("set-user-enabled", {email: email, enabled: true})
               .success(function() { callback(true); })
               .call();
           }},
          {name: "disable",
           button: "secondary",
           showFor: function(user) { return user.enabled; },
           operation: function(email, callback) {
             ajax
               .command("set-user-enabled", {email: email, enabled: false})
               .success(function() { callback(true); })
               .call();
           }},
          {name: "resetPassword",
           button: "secondary",
           showFor: function(user) { return user.enabled; },
           operation: function(email, callback) {
             ajax
               .command("admin-reset-password", {email: email})
               .success(function(e) { callback(e); })
               .call();
           }},
          {name: "toAuthority",
           button: "secondary",
           showFor: function(user) {return (user.enabled && user.role === "applicant") || user.role === "dummy";},
           operation: function(email, callback) {
             ajax
               .command("applicant-to-authority", {email: email})
               .success(function() { callback(true); })
               .call();
           }},
          {name: "retryRakentajafi",
           button: "secondary",
           showFor: function(user) {return user.enabled && user.role === "applicant";},
           operation: function(email, callback) {
             ajax
               .command("retry-rakentajafi", {email: email})
               .success(function() { callback(true); })
               .call();
           }},
          {name: "erase",
           button: "secondary",
           showFor: _.constant(true),
           operation: function (email, callback) {
             ajax
               .command("erase-user", {email: email})
               .success(function () { callback(true); })
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

    self.showForm = ko.observable(false);

    hub.subscribe("admin::authAdminCreated", function() {
      self.showForm(false);
    });

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

  function RestApiUsers() {
    var self = this;
    var backend = "-backend";

    self.organizationCode = ko.observable();
    self.phase = ko.observable(0);
    self.firstName = ko.observable();
    self.lastName = ko.observable("Taustaj\u00E4rjestelm\u00E4");
    self.username = ko.pureComputed(function() {
      return util.lowerCase(self.firstName()) + "-backend";
    });

    self.userDetailsOk = ko.computed(function() {
      var firstNameOk = (self.firstName() && (self.firstName().length + backend.length) < 21);
      var lastNameOk = self.lastName();
      var organizationCodeOk = self.organizationCode();
      var usernameOk = self.username();
      return organizationCodeOk && usernameOk && firstNameOk && lastNameOk;
    });

    self.searching = ko.observable();
    self.userAdded = ko.observable();

    self.createdUserUsername = ko.observable();
    self.createdPw = ko.observable();

    self.clean = function() {
      return self
        .phase(1)
        .organizationCode("")
        .firstName("")
        .lastName("Taustaj\u00E4rjestelm\u00E4")
        .searching(false)
        .userAdded(false)
        .createdPw("")
        .createdUserUsername("");
    };

    self.dialog = function() {
      if (!self._dialog) {
        self._dialog = new LUPAPISTE.Modal("ModalDialogMask", "black");
        self._dialog.createMask();
      }
      return self._dialog;
    };

    self.addRestUser = function() {
      self.clean().dialog().open("#add-rest-api-user-to-organization-dialog");
    };

    self.next = function() {
      self.searching(true).phase(2);
      ajax
        .command("create-rest-api-user",
                 {username: self.username(),
                  organization: self.organizationCode(),
                  firstName: self.firstName(),
                  lastName: self.lastName()})
        .pending(self.searching)
        .success(function(r) {
          self.createdUserUsername(r.user.username);
          self.createdPw(r.user.password);
          self.userAdded(true);
          usersList.redraw();
        })
        .call();
    };
  }

  var restApiUsers = new RestApiUsers();

  function SystemUsers() {
    var self = this;

    self.municipalityName = ko.observable("");
    self.organizationIds = ko.observable();
    self.phase = ko.observable(0);
    self.userDetailsOk = ko.computed(function() {
        var municipalityNameOk = !_.isEmpty(self.municipalityName());
        var organizationIdsOk = self.organizationIds();
        return organizationIdsOk && municipalityNameOk;
    });

    self.searching = ko.observable();
    self.userAdded = ko.observable();

    self.createdUserUsername = ko.observable();

    self.clean = function() {
      return self
        .phase(1)
        .organizationIds("")
        .municipalityName("")
        .searching(false)
        .userAdded(false)
        .createdUserUsername("");
    };

    self.dialog = function() {
      if (!self._dialog) {
        self._dialog = new LUPAPISTE.Modal("ModalDialogMask", "black");
        self._dialog.createMask();
      }
      return self._dialog;
    };

    self.addSystemUser = function() {
      self.clean().dialog().open("#add-system-user-to-organization-dialog");
    };

    self.next = function() {
      self.searching(true).phase(2);
      ajax
        .command("create-system-user",
                 {"municipality-name": self.municipalityName(),
                  "organization-ids": _.split(self.organizationIds(), /[\s,;]+/)})
        .pending(self.searching)
        .success(function(r) {
          self.createdUserUsername(r.username);
          self.userAdded(true);
          usersList.redraw();
        })
        .call();
    };
  }

  var systemUsers = new SystemUsers();

  function FinancialHandlerUser() {
    var self = this;

    self.email = ko.observable();
    self.name = ko.observable();
    self.phase = ko.observable(0);
    self.searching = ko.observable();
    self.userAdded = ko.observable();

    self.userDetailsOk = ko.computed(function() {
      var emailOk = self.email() && util.isValidEmailAddress(self.email());
      var nameOk = self.name();
      return emailOk && nameOk;
    });

    self.createdUserUsername = ko.observable();
    self.createdPw = ko.observable();

    self.createdUserlinkFi = ko.observable();
    self.createdUserlinkSv = ko.observable();
    self.createdUserUsername = ko.observable();

    self.clean = function() {
      return self
        .phase(1)
        .email("")
        .name("")
        .searching(false)
        .userAdded(false);
    };

    self.dialog = function() {
      if (!self._dialog) {
        self._dialog = new LUPAPISTE.Modal("ModalDialogMask", "black");
        self._dialog.createMask();
      }
      return self._dialog;
    };

    self.addFinancialHandlerUser = function () {
      self.clean().dialog().open("#add-financial-user-to-organization-dialog");
    };

    self.next = function() {
      self.searching(true).phase(2);
      ajax
        .command("create-financial-handler",
          {username: self.email(),
            role: "financialAuthority",
            email: self.email(),
            firstName: self.name(),
            lastName: "",
            enabled: "true"})
        .pending(self.searching)
        .success(function(r) {
          self.userAdded(true);
          self.createdUserUsername(r.username);
          self.createdUserlinkFi(r.linkFi);
          self.createdUserlinkSv(r.linkSv);
          usersList.redraw();
        })
        .call();
    };
  }

  var financialHandlerUser = new FinancialHandlerUser();

  $(function() {
    $("#admin").applyBindings({});
    $("#users").applyBindings({
      authorityAdminUsers: authorityAdminUsers,
      restApiUsers: restApiUsers,
      systemUsers: systemUsers,
      financialHandlerUser: financialHandlerUser
    });
  });

})();
