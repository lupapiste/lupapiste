(function() {
  "use strict";

  function NewCompanyUser() {
    var self = this;

    self.token = ko.observable(pageutil.subPage());

    self.loading = ko.observable();
    self.loaded = ko.observable();
    self.notFound = ko.observable();

    self.companyName = ko.observable();
    self.companyY = ko.observable();
    self.firstName = ko.observable();
    self.lastName = ko.observable();
    self.email = ko.observable();

    self.pending = ko.observable(false);
    self.password1 = ko.observable();
    self.password2 = ko.observable();
    self.passwordQuality = ko.computed(function() { return util.getPwQuality(self.password1()); });
    self.ok = ko.computed(function() {
      var t = self.token(),
          p1 = self.password1(),
          p2 = self.password2();
      return t && t.length && p1 && p1.length >= LUPAPISTE.config.passwordMinLength && p1 === p2;
    });
    self.success = ko.observable(false);
    self.fail = ko.observable(false);

    self.reset = function() {
      return self
        .token("")
        .loading(true)
        .loaded(false)
        .notFound(false)
        .companyName("")
        .companyY("")
        .firstName("")
        .lastName("")
        .email("")
        .pending(true)
        .password1("")
        .password2("")
        .success(false)
        .fail(false);
    };

    self.send = function() {
      ajax
        .post("/api/token/" + self.token())
        .json({password: self.password1()})
        .success(function() { self.success(true).fail(false).password1("").password2(""); })
        .fail(function() { self.success(false).fail(true); })
        .call();
    };

    hub.onPageLoad("new-company-user", function(e) {
      self.reset().token(e.pagePath[0]);
      ajax
        .get("/api/token/" + self.token())
        .success(function(data) {
          var token = data.token.data,
              company = token.company,
              user = token.user;
          self
            .companyName(company.name)
            .companyY(company.y)
            .firstName(user.firstName)
            .lastName(user.lastName)
            .email(user.email)
            .loading(false)
            .loaded(true);
        })
        .fail(function() {
          self
            .loading(false)
            .notFound(true);
        })
        .call();
    });

  }


  //
  // Invite:
  //

  function BaseModel() {
    var self = this;
    self.result   = ko.observable("pending");

    self.pending  = ko.pureComputed(function() {
      return self.result() === "pending";
    });
    self.ok = ko.pureComputed(function() {
      return self.result() === "ok";
    });
    self.fail = ko.pureComputed(function() {
      return self.result() === "fail";
    });
  }

  function InviteCompanyUser() {
    var self = this;
    self.open = function(e) {
      self.result("pending");
      ajax
        .post("/api/token/" + e.pagePath[1])
        .json({ok: true})
        .success(_.partial(self.result, "ok"))
        .fail(_.partial(self.result, "fail"))
        .call();
    };

    hub.onPageLoad("invite-company-user", self.open);
  }
  InviteCompanyUser.prototype = new BaseModel();
  InviteCompanyUser.prototype.constructor = InviteCompanyUser;

  function AcceptCompanyInvitation() {
    var self = this;
    self.open = function(e) {
      self.result("pending");
      ajax
        .post("/api/token/" + e.pagePath[0])
        .json({ok: true})
        .success(_.partial(self.result, "ok"))
        .fail(_.partial(self.result, "fail"))
        .call();
    };
    hub.onPageLoad("accept-company-invitation", self.open);
  }

  AcceptCompanyInvitation.prototype = new BaseModel();
  AcceptCompanyInvitation.prototype.constructor = AcceptCompanyInvitation;

  //
  // Initialize:
  //

  var newCompanyUser = new NewCompanyUser();
  var inviteCompanyUser = new InviteCompanyUser();
  var acceptCompanyInvitation = new AcceptCompanyInvitation();

  $(function() {
    $("section#new-company-user").applyBindings(newCompanyUser);
    $("section#invite-company-user").applyBindings(inviteCompanyUser);
    $("section#accept-company-invitation").applyBindings(acceptCompanyInvitation);
  });

})();
