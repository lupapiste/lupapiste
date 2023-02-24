(function() {
  "use strict";

  var required = {required: true};


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

    self.email     = ko.observable().extend({required: true,
                                             email: true});
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
    self.showUserNotApplicant = ko.observable();
    self.oldUser          = ko.observable();
    self.isDummy          = ko.pureComputed(function() { return self.userRole() === "dummy"; });

    self.canSearchUser    = self.email.isValid;
    self.pending          = ko.observable();

    self.emailEnabled     = ko.observable();
    self.done             = ko.observable();

    self.canSubmit = ko.computed(function() { return !self.pending() && !self.done() && namesValid();}, self);
    self.canClose  = ko.computed(function() { return !self.pending(); }, self);

    self.searchUser = function() {
      self.emailEnabled(false);
      self.oldUser( false );
      ajax
        .query("company-search-user", {email: self.email()})
        .pending(self.pending)
        .success(function(data) {
          var result = data.result;
          if (result === "already-in-company") {
            self.showSearchEmail(false).showUserInCompany(true);
          } else if (result === "already-invited") {
            self.showSearchEmail(false).showUserAlreadyInvited(true);
          } else if (result === "not-applicant") {
            self.showSearchEmail(false).showUserNotApplicant(true);
          } else {
            self.showSearchEmail(false).showUserDetails(true);
            if( result === "found") {
              self.oldUser( true );
              self.firstName( data.firstName );
              self.lastName( data.lastName );
              self.userRole( data.role );
            }
          }
        })
        .call();
    };

    self.sendInvite = function() {
      ajax
        .command(self.oldUser() ? "company-invite-user" :"company-add-user",
                 {email: self.email(),
                  firstName: self.firstName(),
                  lastName: self.lastName(),
                  admin: self.admin(),
                  submit: self.submit()})
        .pending(self.pending)
        .success(function() {
          hub.send("refresh-companies");
          self.done(true);
        }, self)
        .call();
    };

    self.open = function() {
      ko.mapping.fromJS(self.defaults, {}, self);
      self
        .pending(false)
        .done(false)
        .emailEnabled(true)
        .showSearchEmail(true)
        .showUserInCompany(undefined)
        .showUserAlreadyInvited(undefined)
        .showUserInvited(undefined)
        .showUserDetails(undefined)
        .showUserNotApplicant(undefined);
      LUPAPISTE.ModalDialog.open("#dialog-company-new-user");
    };
  }

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
    hub.send( "show-dialog", {ltitle: prefix + "title",
                              component: "yes-no-dialog",
                              size: "medium",
                              componentParams: {
                                text: loc(prefix + "message",
                                          htmlSafeName( user )) + "<br>",
                                yesFn: deleteCommand,
                              }

                             });
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
        hub.send( "show-dialog", {ltitle: prefix  + "title",
                                  size: "medium",
                                  component: "yes-no-dialog",
                                  componentParams: {
                                    text: loc(prefix + "message",
                                              htmlSafeName( user )),
                                    yesFn: self.ok}});
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
    self.opsEnabled   = ko.computed(function() {
      return lupapisteApp.models.currentUser.company.role() === "admin"
          && lupapisteApp.models.currentUser.id() !== user.id; });
    self.deleteInvitation = _.partial( deleteCompanyUser, user, function() {
      invitations.remove(function(i) { return i.tokenId === user.tokenId; });
    } );
  }


  // ========================================================================================
  // CompanyModel:
  // ========================================================================================

  function CompanyInfo(parent) {
    var self = this;
    self.parent = parent;
    self.model = ko.validatedObservable({
      accountType:  ko.observable().extend(required),
      billingType:  ko.observable(),
      customAccountLimit: ko.observable(),
      name:           ko.observable().extend(required),
      y:              ko.observable(),
      reference:      ko.observable(),
      address1:       ko.observable().extend(required),
      po:             ko.observable().extend(required),
      zip:            ko.observable().extend({required: true,
                                              number: true,
                                              maxLength: 5,
                                              minLength: 5}),
      country:        ko.observable(),
      netbill:        ko.observable(),
      pop:            ko.observable(),
      contactAddress: ko.observable(),
      contactZip:     ko.observable().extend( {number: true,
                                               maxLength: 5,
                                               minLength: 5}),
      contactPo:      ko.observable(),
      contactCountry: ko.observable(),
      invitationDenied: ko.observable()
    });

    self.defaults = {
      name: undefined,
      y: undefined,
      reference: undefined,
      address1: undefined,
      po: undefined,
      zip: undefined,
      country: undefined,
      netbill: undefined,
      pop: undefined,
      accountType: undefined,
      billingType: "monthly",
      customAccountLimit: undefined,
      contactAddress: undefined,
      contactZip:     undefined,
      contactPo:      undefined,
      contactCountry: undefined,
      invitationDenied: false
    };
    self.edit          = ko.observable(false);
    self.saved         = ko.observable(null);
    self.canStartEdit  = ko.computed(function() {
      return !self.edit()
        && parent.isAdmin()
        && !self.isLocked();
    });
    self.changed       = ko.computed(function() {
      return !_.isEqual(ko.mapping.toJS(self.model()), self.saved());
    });
    self.canSubmit     = ko.computed(function() {
      return self.edit() && self.model.isValid() && self.changed();
    });
    self.accountType   = ko.observable();
    self.accountTypes  = ko.observableArray();
    self.isLocked      = ko.computed( _.wrap( "user-company-locked",
                                              lupapisteApp.models.globalAuthModel.ok ));

    self.setAccountTypeOptionDisable = function(option, item) {
      ko.applyBindingsToNode(option, {disable: item ? item.disable : false}, item);
    };

    self.updateAccountTypes = function(company) {
      if (_.isEmpty(company)) {
        self.accountTypes([]);
      } else {
        var accountType = self.accountType;
        accountType(_.find(LUPAPISTE.config.accountTypes, {name: company.accountType}));
        var mappedAccountTypes = _.map(LUPAPISTE.config.accountTypes, function(type) {
          type.disable = ko.observable(accountType() ? type.limit < accountType().limit : false);
          var price = util.getIn(type, ["price", company.billingType]);
          var accountTitle = loc(sprintf( "register.company.%s.title", type.name));
          var priceText = loc("register.company.billing." + company.billingType + ".price", price);
          type.displayName = sprintf("%s (%s)", accountTitle, priceText);
          return type;
        });
        self.accountTypes([]);
        self.accountTypes(mappedAccountTypes);
      }
    };

    self.update = function(company) {
      self.updateAccountTypes(company);
      ko.mapping.fromJS(_.merge(self.defaults, company), {
        ignore:["id"]
      }, self.model());
      return self
        .edit(false)
        .saved(null)
        .parent;
    };

    self.clear = function() {
      return self.update({});
    };

    self.startEdit = function() {
      return self
        .saved(ko.mapping.toJS(self.model()))
        .edit(true);
    };

    self.cancelEdit = function() {
      ko.mapping.fromJS(self.saved(), {}, self.model());
      return self
        .edit(false)
        .saved(null);
    };

    self.submit = function() {
      ajax
        .command("company-update", {company: self.parent.id(),
                                    updates: util.dissoc(ko.mapping.toJS(self.model()), "y")})
        .pending(self.parent.pending)
        .success(function(data) { self.update(data.company); }, self)
        .call();
      return false;
    };
  }

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
    self.currentView = ko.observable();
    self.toggles      = _.map(["info", "users", "tags"],
                              function( tab ) {
                                return {
                                  value: tab,
                                  lText: "company.tab." + tab,
                                  click: function() {
                                    pageutil.openPage("company", self.id() + "/" + tab);
                                  }
                                };});
    self.indicator   = ko.observable(false).extend({notify: "always"});

    self.backClick = function() {
      pageutil.openPage( "mypage" );
    };

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

      self.currentView( tab || "info" );
      return self;
    };

    self.openNewUser = function() {
      newCompanyUser.open();
    };

    self.nukeAll = function() {
      hub.send("show-dialog", {ltitle: "company.delete-all",
                               size: "medium",
                               component: "yes-no-dialog",
                               componentParams: {ltext: "company.delete-all.confirmation",
                                                 yesFn: function() {
                                                   ajax.command( "company-user-delete-all", {})
                                                   .success( _.wrap( "logout", hub.send))
                                                   .call();
                                                 },
                                                 lyesTitle: "yes",
                                                 lnoTitle: "cancel"} });
    };

    self.setDenied = function () {
      ajax
        .command("company-update", {company: self.id(),
                                    updates: {invitationDenied: self.info.model().invitationDenied()}})
        .pending(self.pending)
        .success(util.showSavedIndicator)
        .call();
      return true;
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
    $("#company-reports").applyBindings(company);
  });

})();
