(function() {
  "use strict";

  var required      = {required: true},
      notRequired   = {required: false};

  // ========================================================================================
  // NewCompanyUser:
  // ========================================================================================

  function NewCompanyUser() {
    this.defaults  = {
      email: undefined,
      firstName: undefined,
      lastName: undefined,
      admin: undefined
    };

    this.email     = ko.observable().extend(required).extend({email: true});
    this.firstName = ko.observable().extend(required);
    this.lastName  = ko.observable().extend(required);
    this.admin     = ko.observable().extend(notRequired);

    this.isValid = ko.computed(function() {
      return _.every(this.fields, function(f) { return this[f].isValid(); }, this);
    }, this);

    this.showSearchEmail    = ko.observable();
    this.showUserInCompany  = ko.observable();
    this.showUserAlreadyInvited = ko.observable();
    this.showUserInvited    = ko.observable();
    this.showUserDetails    = ko.observable();

    this.canSearchUser    = this.email.isValid;
    this.pending          = ko.observable();
    this.canCancel        = ko.computed(function() { return this.pending(); }, this);

    this.emailEnabled     = ko.observable();
    this.done             = ko.observable();

    this.canSubmit = ko.computed(function() { return !this.pending() && !this.done() && this.isValid(); }, this);
    this.canClose  = ko.computed(function() { return !this.pending(); }, this);
  }

  NewCompanyUser.prototype.update = function(source) {
    ko.mapping.fromJS(_.merge(this.defaults, source), {}, this);
    return this;
  };

  NewCompanyUser.prototype.searchUser = function() {
    this.emailEnabled(false);
    ajax
      .query("company-invite-user", {email: this.email()})
      .pending(this.pending)
      .success(function(data) {
        var result = data.result;
        if (result === "invited") {
          hub.send("refresh-companies");
          this.showSearchEmail(false).showUserInvited(true);
        } else if (result === "already-in-company") {
          this.showSearchEmail(false).showUserInCompany(true);
        } else if (result === "already-invited") {
          this.showSearchEmail(false).showUserAlreadyInvited(true);
        } else if (result === "not-found") {
          this.showSearchEmail(false).showUserDetails(true);
        }
      }, this)
      .call();
  };

  NewCompanyUser.prototype.submit = function() {
    ajax
      .command("company-add-user", ko.mapping.toJS(this))
      .pending(this.pending)
      .success(function() {
          hub.send("refresh-companies");
          this.done(true);
        }, this)
      .call();
  };

  NewCompanyUser.prototype.open = function() {
    ko.mapping.fromJS(this.defaults, {}, this);
    this
      .pending(false)
      .done(false)
      .emailEnabled(true)
      .showSearchEmail(true)
      .showUserInCompany(undefined)
      .showUserAlreadyInvited(undefined)
      .showUserInvited(undefined)
      .showUserDetails(undefined);
    LUPAPISTE.ModalDialog.open("#dialog-company-new-user");
  };

  var newCompanyUser = new NewCompanyUser();

  // ========================================================================================
  // CompanyUserOp:
  // ========================================================================================

  function CompanyUserOp() {
    var self = this;

    self.title    = ko.observable();
    self.message  = ko.observable();
    self.pending  = ko.observable();

    self.userId   = null;
    self.tokenId  = null;
    self.op       = null;
    self.value    = null;
    self.cb       = null;

    self.withConfirmation = function(user, value, op, cb) {
      return function() {
        self.value    = value ? value : ko.observable(true);
        self.userId   = user.id;
        self.tokenId  = user.tokenId;
        self.op       = op;
        self.cb       = cb;
        var prefix    = "company.user.op." + op + "." + self.value() + ".",
            userName  = user.firstName + " " + user.lastName;
        self
          .title(loc(prefix + "title"))
          .message(loc(prefix + "message", userName))
          .pending(false);
        LUPAPISTE.ModalDialog.open("#dialog-company-user-op");
      };
    };

    self.ok = function() {
      self.pending(true);
      var command = "company-user-update";
      var params = {"user-id": self.userId, op: self.op, value: !self.value()};
      if (self.tokenId) {
        command = "company-cancel-invite";
        params = {"tokenId": self.tokenId};
      }
      ajax
        .command(command, params)
        .success(function() {
          if (self.cb) {
            self.cb();
            lupapisteApp.models.globalAuthModel.refresh();
          } else {
            self.value(!self.value());
          }
          LUPAPISTE.ModalDialog.close();
        })
        .call();
    };
  }

  var companyUserOp = new CompanyUserOp();

  // ========================================================================================
  // CompanyUser:
  // ========================================================================================

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
    self.opsEnabled   = ko.computed(function() { return lupapisteApp.models.currentUser.company.role() === "admin" && lupapisteApp.models.currentUser.id() !== user.id; });
    self.toggleAdmin  = companyUserOp.withConfirmation(user, self.admin, "admin");
    self.toggleEnable = companyUserOp.withConfirmation(user, self.enabled, "enabled");
    self.deleteUser   = companyUserOp.withConfirmation(user, self.deleted, "delete", function() {
      users.remove(function(u) { return u.id === self.id; });
    });
  }

  function InvitedUser(user, invitations) {
    var self = this;
    self.firstName = user.firstName;
    self.lastName  = user.lastName;
    self.email     = user.email;
    self.expires   = user.expires;
    self.role      = user.role;
    self.tokenId   = user.tokenId;
    self.opsEnabled   = ko.computed(function() { return lupapisteApp.models.currentUser.company.role() === "admin" && lupapisteApp.models.currentUser.id() !== user.id; });
    self.deleteInvitation = companyUserOp.withConfirmation(user, self.deleted, "delete-invite", function() {
      invitations.remove(function(i) { return i.tokenId === user.tokenId; });
    });
  }

  // ========================================================================================
  // Tab and Tabs:
  // ========================================================================================

  function Tab(parent, name) {
    this.parent  = parent;
    this.name    = name;
    this.active  = ko.observable(false);
  }

  Tab.prototype.click = function(m) {
    m.parent.click(m.name);
  };

  function TabsModel(companyId) {
    this.tabs = _.map(["info", "users"], function(name) { return new Tab(this, name); }, this);
    this.companyId = companyId;
  }

  TabsModel.prototype.click = function(tab) {
    window.location.hash = "!/company/" + this.companyId() + "/" + tab;
    return false;
  };

  TabsModel.prototype.show = function(name) {
    name = _.isBlank(name) ? "info" : name;
    _.each(this.tabs, function(tab) { tab.active(tab.name === name); });
    return this;
  };

  TabsModel.prototype.visible = function(name) {
    return _.find(this.tabs, {name: _.isBlank(name) ? "info" : name}).active;
  };

  // ========================================================================================
  // CompanyModel:
  // ========================================================================================

  function CompanyInfo(parent) {
    this.parent = parent;
    this.model = ko.validatedObservable({
      accountType:  ko.observable().extend(required),
      customAccountLimit: ko.observable(),
      name:         ko.observable().extend(required),
      y:            ko.observable(),
      reference:    ko.observable().extend(notRequired),
      address1:     ko.observable().extend(required),
      po:           ko.observable().extend(required),
      zip:          ko.observable().extend({required: true, number: true, maxLength: 5}),
      country:      ko.observable().extend(notRequired),
      ovt:          ko.observable().extend(notRequired).extend({ovt: true}),
      pop:          ko.observable().extend(notRequired)
    });
    this.defaults = {
      name: undefined,
      y: undefined,
      reference: undefined,
      address1: undefined,
      po: undefined,
      zip: undefined,
      country: undefined,
      ovt: undefined,
      pop: undefined,
      accountType: undefined,
      customAccountLimit: undefined
    };
    this.edit          = ko.observable(false);
    this.saved         = ko.observable(null);
    this.canStartEdit  = ko.computed(function() { return !this.edit() && parent.isAdmin(); }, this);
    this.changed       = ko.computed(function() { return !_.isEqual(ko.mapping.toJS(this.model()), this.saved()); }, this);
    this.canSubmit     = ko.computed(function() { return this.edit() && this.model.isValid() && this.changed(); }, this);
    this.accountTypes  = ko.observableArray();
  }

  CompanyInfo.prototype.setAccountTypeOptionDisable = function(option, item) {
    ko.applyBindingsToNode(option, {disable: item ? item.disable : false}, item);
  };

  CompanyInfo.prototype.updateAccountTypes = function(company) {
    var currentAccountType = _.findWhere(LUPAPISTE.config.accountTypes, {name: company.accountType});
    var mappedAccountTypes = _.map(LUPAPISTE.config.accountTypes, function(type) {
      type.disable = ko.observable(currentAccountType ? type.limit < currentAccountType.limit : false);
      type.displayName = loc("register.company." + type.name + ".title") + " (" + loc("register.company." + type.name + ".price") + ")";
      return type;
    });
    this.accountTypes([]);
    this.accountTypes(mappedAccountTypes);
  };

  CompanyInfo.prototype.update = function(company) {
    this.updateAccountTypes(company);
    ko.mapping.fromJS(_.merge(this.defaults, company), {
      ignore:["id"]
    }, this.model());
    return this
      .edit(false)
      .saved(null)
      .parent;
  };

  CompanyInfo.prototype.clear = function() {
    return this.update({});
  };

  CompanyInfo.prototype.startEdit = function() {
    return this
      .saved(ko.mapping.toJS(this.model()))
      .edit(true);
  };

  CompanyInfo.prototype.cancelEdit = function() {
    ko.mapping.fromJS(this.saved(), {}, this.model());
    return this
      .edit(false)
      .saved(null);
  };

  CompanyInfo.prototype.submit = function() {
    ajax
      .command("company-update", {company: this.parent.id(), updates: util.dissoc(ko.mapping.toJS(this.model()), "y")})
      .pending(this.parent.pending)
      .success(function(data) { this.update(data.company); }, this)
      .call();
    return false;
  };

  // ========================================================================================
  // Company:
  // ========================================================================================

  function Company() {
    var self = this;

    self.pending     = ko.observable();
    self.id          = ko.observable();
    self.isAdmin     = ko.observable();
    self.users       = ko.observableArray();
    self.invitations = ko.observableArray();
    self.info        = new CompanyInfo(self);
    self.tabs        = new TabsModel(self.id);

    self.clear = function() {
      return self
        .pending(false)
        .id(null)
        .info.clear()
        .users(null)
        .isAdmin(null);
    };

    self.update = function(data) {
      return self
        .id(data.company.id)
        .info.update(data.company)
        .isAdmin(lupapisteApp.models.currentUser.role() === "admin" || (lupapisteApp.models.currentUser.company.role() === "admin" && lupapisteApp.models.currentUser.company.id() === self.id()))
        .users(_.map(data.users, function(user) { return new CompanyUser(user, self.users); }))
        .invitations(_.map(data.invitations, function(invitation) { return new InvitedUser(invitation, self.invitations); }));
    };

    self.load = function() {
      ajax
        .query("company", {company: self.id(), users: true})
        .pending(self.pending)
        .success(self.update)
        .call();
      return self;
    };

    self.show = function(id, tab) {
      if (self.id() !== id) { self.clear().id(id).load(); }
      self.tabs.show(tab);
      return self;
    };

    self.openNewUser = function() {
      newCompanyUser.open();
    };

  }

  var company = new Company();

  hub.onPageLoad("company", function(e) { company.show(e.pagePath[0], e.pagePath[1]); });

  hub.subscribe("refresh-companies", function() {
    lupapisteApp.models.globalAuthModel.refresh();
    company.load();
  });

  $(function() {
    $("#company-content").applyBindings(company);
    $("#dialog-company-user-op").applyBindings(companyUserOp);
    $("#dialog-company-new-user").applyBindings(newCompanyUser);
  });

})();
