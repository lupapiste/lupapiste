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
      return t && t.length && p1 && p1.length > 5 && p1 === p2;
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

    hub.onPageChange("new-company-user", function(e) {
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

  function InviteCompanyUser() {
    this.result   = ko.observable("pending");
    this.pending  = ko.computed(function() { return this.result() === "pending"; }, this);
    this.ok       = ko.computed(function() { return this.result() === "ok"; }, this);
    this.fail     = ko.computed(function() { return this.result() === "fail"; }, this);
    hub.onPageChange("invite-company-user", this.open.bind(this));
  }

  InviteCompanyUser.prototype.open = function(e) {
    this.result("pending");
    ajax
      .post("/api/token/" + e.pagePath[1])
      .json({ok: true})
      .success(this.result.bind(this, "ok"))
      .fail(this.result.bind(this, "fail"))
      .call();
  };

  function AcceptCompanyInvitation() {
    this.result   = ko.observable("pending");
    this.pending  = ko.computed(function() { return this.result() === "pending"; }, this);
    this.ok       = ko.computed(function() { return this.result() === "ok"; }, this);
    this.fail     = ko.computed(function() { return this.result() === "fail"; }, this);
    hub.onPageChange("accept-company-invitation", this.open.bind(this));
  }

  AcceptCompanyInvitation.prototype.open = function(e) {
    this.result("pending");
    ajax
      .post("/api/token/" + e.pagePath[0])
      .json({ok: true})
      .success(this.result.bind(this, "ok"))
      .fail(this.result.bind(this, "fail"))
      .call();
  };

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
