(function() {
  "use strict";

  function NewCompanyUser() {
    var required = {required: true, minLength: 1};

    this.model = ko.validatedObservable({
      firstName: ko.observable().extend(required),
      lastName:  ko.observable().extend(required),
      email:     ko.observable().extend(required).extend({pattern: {message: "S\u00E4hk\u00F6postiosoite", params: "^\\S+@\\S+$"}}),
      admin:     ko.observable().extend({required: false})
    });

    this.pending   = ko.observable();
    this.done      = ko.observable(false);
    this.canSubmit = ko.computed(function() { return !this.pending() && this.model.isValid() && !this.done(); }, this);
    this.canClose  = ko.computed(function() { return !this.pending(); }, this);

    this.open = function() {
      this
        .pending(false)
        .done(false)
        .model()
          .firstName(null)
          .lastName(null)
          .email(null)
          .admin(false);
      LUPAPISTE.ModalDialog.open("#dialog-company-new-user");
    };

    this.submit = function() {
      var m = this.model(),
          data = _.reduce(
              ["firstName", "lastName", "email", "admin"],
              function(acc, k) { acc[k] = m[k](); return acc; },
              {});
      ajax
        .command("company-add-user", data)
        .pending(this.pending)
        .success(this.done.bind(this, true))
        .call();
    };
  }

  var newCompanyUser = new NewCompanyUser();

  function CompanyUserOp() {
    var self = this;

    self.title    = ko.observable();
    self.message  = ko.observable();
    self.pending  = ko.observable();

    self.userId = null;
    self.op     = null;
    self.value  = null;
    self.cb     = null;

    self.withConfirmation = function(user, value, op, cb) {
      return function() {
        self.value  = value ? value : ko.observable(true);
        self.userId = user.id;
        self.op     = op;
        self.cb     = cb;
        var prefix   = "company.user.op." + op + "." + self.value() + ".",
            userName = user.firstName + " " + user.lastName;
        self
          .title(loc(prefix + "title"))
          .message(loc(prefix + "message", userName))
          .pending(false);
        LUPAPISTE.ModalDialog.open("#dialog-company-user-op");
      };
    };

    self.ok = function() {
      self.pending(true);
      ajax
        .command("company-user-update", {"user-id": self.userId, op: self.op, value: !self.value()})
        .success(function() {
          if (self.cb) {
            self.cb();
          } else {
            self.value(!self.value());
          }
          LUPAPISTE.ModalDialog.close();
        })
        .call();
    };
  }

  var companyUserOp = new CompanyUserOp();

  function CompanyUser(user, users) {
    var self = this;
    self.id           = user.id;
    self.firstName    = user.firstName;
    self.lastName     = user.lastName;
    self.email        = user.email;
    self.enabled      = ko.observable(user.enabled);
    self.role         = ko.observable(user.company.role);
    self.admin        = ko.computed({
      read:  function() { return self.role() === "admin"; },
      write: function(v) { return self.role(v ? "admin" : "user"); }
    });
    self.opsEnabled   = ko.computed(function() { return currentUser.get().company.role() === "admin" && currentUser.id() !== user.id; });
    self.toggleAdmin  = companyUserOp.withConfirmation(user, self.admin, "admin");
    self.toggleEnable = companyUserOp.withConfirmation(user, self.enabled, "enabled");
    self.deleteUser   = companyUserOp.withConfirmation(user, self.deleted, "delete", function() {
      users.remove(function(u) { return u.id === self.id; });
    });
  }

  function Company() {
    var self = this;

    self.pending  = ko.observable();
    self.id       = ko.observable();
    self.name     = ko.observable();
    self.y        = ko.observable();
    self.users    = ko.observableArray();
    self.isAdmin  = ko.observable();

    self.clear = function() {
      _(self).values().filter(ko.isObservable).each(function(o) { o(null); });
      return self;
    };

    self.update = function(data) {
      return self
        .id(data.company.id)
        .name(data.company.name)
        .y(data.company.y)
        .isAdmin(currentUser.get().role() === "admin" || (currentUser.get().company.role() === "admin" && currentUser.get().company.id() === self.id()))
        .users(_.map(data.users, function(user) { return new CompanyUser(user, self.users); }));
    };

    self.load = function() {
      ajax
        .query("company", {company: self.id(), users: true})
        .pending(self.pending)
        .success(self.update)
        .call();
      return self;
    };

    self.show = function(id) {
      return (self.id() === id) ? self : self.clear().id(id).load();
    };

    self.openNewUser = function() {
      newCompanyUser.open();
    };
  }

  var company = new Company();

  hub.onPageChange("company", function(e) { company.show(e.pagePath[0]); });

  $(function() {
    $("#company-content").applyBindings(company);
    $("#dialog-company-user-op").applyBindings(companyUserOp);
    $("#dialog-company-new-user").applyBindings(newCompanyUser);
  });

})();
