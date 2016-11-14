(function() {
  "use strict";

  var required      = {required: true},
      notRequired   = {required: false};

  // ========================================================================================
  // NewCompanyUser:
  // ========================================================================================

  function NewCompanyUser() {
    var self = this;
    self.defaults  = {
      email: undefined,
      firstName: undefined,
      lastName: undefined,
      admin: false,
      submit: true
    };

    self.email     = ko.observable().extend(required).extend({email: true});
    self.firstName = ko.observable().extend(required);
    self.lastName  = ko.observable().extend(required);
    self.userRole  = ko.observable();
    self.admin     = ko.observable();
    self.submit    = ko.observable();

    // Checking the required name fields without triggering validation.
    var namesValid = ko.pureComputed(function() {
      return _.every( [self.firstName(), self.lastName()], _.trim );
    } );

    self.showSearchEmail    = ko.observable();
    self.showUserInCompany  = ko.observable();
    self.showUserAlreadyInvited = ko.observable();
    self.showUserInvited    = ko.observable();
    self.showUserDetails    = ko.observable();
    self.oldUser          = ko.observable();
    self.isDummy          = ko.pureComputed(function() { return self.userRole() === "dummy"; });

    self.canSearchUser    = self.email.isValid;
    self.pending          = ko.observable();

    self.emailEnabled     = ko.observable();
    self.done             = ko.observable();

    self.canSubmit = ko.computed(function() { return !self.pending() && !self.done() && namesValid();}, self);
    self.canClose  = ko.computed(function() { return !self.pending(); }, self);
  }

  NewCompanyUser.prototype.update = function(source) {
    ko.mapping.fromJS(_.merge(this.defaults, source), {}, this);
    return this;
  };

  NewCompanyUser.prototype.searchUser = function() {
    this.emailEnabled(false);
    this.oldUser( false );
    ajax
      .query("company-search-user", {email: this.email()})
      .pending(this.pending)
      .success(function(data) {
        var result = data.result;
        if (result === "already-in-company") {
          this.showSearchEmail(false).showUserInCompany(true);
        } else if (result === "already-invited") {
          this.showSearchEmail(false).showUserAlreadyInvited(true);
        } else {
          this.showSearchEmail(false).showUserDetails(true);
          if( result === "found") {
            this.oldUser( true );
            this.firstName( data.firstName );
            this.lastName( data.lastName );
            this.userRole( data.role );
          }
        }
      }, this)
      .call();
  };

  NewCompanyUser.prototype.sendInvite = function() {
    ajax
      .command(this.oldUser() ? "company-invite-user" :"company-add-user",
               ko.mapping.toJS(this))
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
  // Delete/uninvite company user
  // ========================================================================================

  function htmlSafeName( user ) {
    return _.escape( user.firstName + " " + user.lastName );
  }

  function deleteCompanyUser( user, callback ) {
    function deleteCommand() {
      ajax.command(user.tokenId ? "company-cancel-invite" : "company-user-delete",
                   user.tokenId ? {tokenId: user.tokenId} : {"user-id": user.id})
        .success( function() {
          callback();
          lupapisteApp.models.globalAuthModel.refresh();
        })
        .call();
    }

    var prefix =  "company.user.op." + (user.tokenId
                                        ? "delete-invite"
                                        : "delete") + ".true.";
    LUPAPISTE.ModalDialog.showDynamicYesNo( loc( prefix  + "title" ),
                                            loc(prefix + "message",
                                                htmlSafeName( user )) + "<br>",
                                            {title: loc( "yes"),
                                             fn: deleteCommand},
                                            {title: loc( "no")},
                                            {html: true});
  }

  // ========================================================================================
  // Editor for role and submit status
  // ========================================================================================

  function UserEditor() {
    var self = this;
    var user = null;
    self.userId = ko.observable();
    self.role = ko.observable();
    self.submit = ko.observable();

    self.edit = function( target ) {
      user = target;
      self.userId( user.id );
      self.role( user.role() );
      self.submit( user.submit());

    };
    self.save = function() {
      if( self.role() !== user.role() ) {
        var prefix = "company.user.op.admin." + (self.role() !== "admin") + ".";
        LUPAPISTE.ModalDialog.showDynamicYesNo( loc( prefix  + "title" ),
                                                loc(prefix + "message",
                                                    htmlSafeName( user )),
                                                {title: loc( "yes"), fn: self.ok},
                                                {title: loc( "no")},
                                                {html: true});
      } else {
        self.ok();
      }
  };
    self.ok = function() {
      ajax.command( "company-user-update",
                    {"user-id": self.userId(),
                     role: self.role(),
                     submit: self.submit()})
        .success( function() {
          user.role( self.role());
          user.submit( self.submit());
          self.clear();
        })
        .call();
    };
    self.clear = function() {
      user = null;
      self.userId( null );
    };
    self.editing = function( user ) {
      return self.userId() === user.id;
    };
    self.roles = ["user", "admin"];
    self.roleText = function( role ) {
      return loc( "company.user.role." + role );
    };
    self.submitText = function( submit ) {
      return loc( submit ? "yes" : "no");
    };
  }

  var userEditor = new UserEditor();

  // ========================================================================================
  // CompanyUser:
  // ========================================================================================

  function CompanyUser(user, users) {
    var self = this;
    self.id           = user.id;
    self.firstName    = user.firstName;
    self.lastName     = user.lastName;
    self.email        = user.email;
    self.enabled      = user.enabled;
    self.role         = ko.observable(user.company.role);
    self.submit       = ko.observable(user.company.submit);

    self.opsEnabled   = ko.computed(function() {
      return lupapisteApp.models.currentUser.company.role() === "admin"
        && lupapisteApp.models.currentUser.id() !== user.id;
    });
    self.deleteUser   = _.partial( deleteCompanyUser, user, function() {
      users.remove(function(u) { return u.id === self.id; });
    } );
    self.editing = ko.computed( _.partial( userEditor.editing, self ));
    self.edit = _.partial( userEditor.edit, self );
  }

  function InvitedUser(user, invitations) {
    var self = this;
    self.firstName = user.firstName;
    self.lastName  = user.lastName;
    self.email     = user.email;
    self.expires   = user.expires;
    self.role      = user.role;
    self.submit    = user.submit;
    self.tokenId   = user.tokenId;
    self.opsEnabled   = ko.computed(function() { return lupapisteApp.models.currentUser.company.role() === "admin" && lupapisteApp.models.currentUser.id() !== user.id; });
    self.deleteInvitation = _.partial( deleteCompanyUser, user, function() {
      invitations.remove(function(i) { return i.tokenId === user.tokenId; });
    } );
  }

  // ========================================================================================
  // Tabs:
  // ========================================================================================

  function TabsModel(companyId) {
    this.tabs = _.map(["info", "users"], function(name) {
      return {
        name: name,
        active: ko.observable(false),
        click: function() {
          pageutil.openPage("company", companyId() + "/" + name);
          return false;
        }
      };});
  }

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
      netbill:      ko.observable().extend(notRequired),
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
      netbill: undefined,
      pop: undefined,
      accountType: undefined,
      customAccountLimit: undefined
    };
    this.edit          = ko.observable(false);
    this.saved         = ko.observable(null);
    this.canStartEdit  = ko.computed(function() { return !this.edit() && parent.isAdmin(); }, this);
    this.changed       = ko.computed(function() { return !_.isEqual(ko.mapping.toJS(this.model()), this.saved()); }, this);
    this.canSubmit     = ko.computed(function() { return this.edit() && this.model.isValid() && this.changed(); }, this);
    this.accountType   = ko.observable();
    this.accountTypes  = ko.observableArray();
  }

  CompanyInfo.prototype.setAccountTypeOptionDisable = function(option, item) {
    ko.applyBindingsToNode(option, {disable: item ? item.disable : false}, item);
  };

  CompanyInfo.prototype.updateAccountTypes = function(company) {
    var accountType = this.accountType; // take correct observable to var, 'this' context differs in _.map's function
    accountType(_.find(LUPAPISTE.config.accountTypes, {name: company.accountType}));
    var mappedAccountTypes = _.map(LUPAPISTE.config.accountTypes, function(type) {
      type.disable = ko.observable(accountType() ? type.limit < accountType().limit : false);
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
    self.users       = ko.observableArray([]);
    self.userEditor  = userEditor;
    self.invitations = ko.observableArray([]);
    self.info        = new CompanyInfo(self);
    self.tabs        = new TabsModel(self.id);

    self.clear = function() {
      return self
        .pending(false)
        .id(null)
        .info.clear()
        .users([])
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
      if (self.id() && lupapisteApp.models.globalAuthModel.ok("company")) {
        ajax
          .query("company", {company: self.id(), users: true})
          .pending(self.pending)
          .success(self.update)
          .error(function() {
            notify.error(loc("error.dialog.title"), loc("error.company-not-accessible"));
            self.id(null);
            pageutil.openPage("applications");
          })
          .call();
        return self;
      }
    };

    self.show = function(id, tab) {
      if (!id) {
        pageutil.openPage("register-company-account-type");
      } else if (self.id() !== id) {
        self.clear().id(id).load();
      }
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
//    $("#dialog-company-user-op").applyBindings(companyUserOp);
    $("#dialog-company-new-user").applyBindings(newCompanyUser);
  });

})();
