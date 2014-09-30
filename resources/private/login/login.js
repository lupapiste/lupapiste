(function() {
  "use strict";

  var rememberMeCookieName = "my-email";

  var rememberMe = ko.observable(false);

  function recallMe() {
    var oldUsername = _.trim($.cookie(rememberMeCookieName));
    if (oldUsername) {
      rememberMe(true);
      $("#login-username").val(oldUsername.toLowerCase());
      $("#login-password").focus();
    } else {
      rememberMe(false);
      $("#login-username").focus();
    }
  }

  function login() {
    var username = _.trim($("#login-username").val());
    var password = $("#login-password").val();
    $("#login-message").text("").css('display', 'none');

    if (rememberMe()) {
      $.cookie(rememberMeCookieName, username.toLowerCase(), { expires: 365, path: "/", secure: LUPAPISTE.config.cookie.secure});
    } else {
      $.removeCookie(rememberMeCookieName, {path: "/"});
    }

    ajax.postJson("/api/login", {"username": username, "password": password})
      .raw(false)
      .success(function(e) {
        var applicationpage = e.applicationpage;
        var redirectLocation = "/app/" + loc.getCurrentLanguage() + "/" + applicationpage;
        // get the server-stored hashbang to redirect to right page (see web.clj for details)
        ajax.get("/api/hashbang")
          .success(function(e) { window.parent.location = redirectLocation + "#!/" + e.bang; })
          .error(function() { window.parent.location = redirectLocation; })
          .call();
      })
      .error(function(e) { hub.send("login-failure", e); })
      .call();
  }

  hub.subscribe("login-failure", function(e) {
    $("#login-message").text(loc(e.text)).css('display', 'block');
  });

  function Reset() {
    var self = this;
    self.email = ko.observable();
    self.ok = ko.computed(function() { var v = self.email(); return v && v.length > 0; });
    self.sent = ko.observable(false);
    self.fail = ko.observable();
    self.send = function() {
      var email = self.email();
      if (!_.isBlank(email)) {
        ajax
          .postJson("/api/reset-password", {"email": email})
          .raw(false)
          .success(function() { self.sent(true).fail(null).email(""); })
          .error(function(e) { self.sent(false).fail(e.text); $("#reset input:first").focus(); })
          .call();
      }
    };
  }

  function quality(pw) {
    if (!pw)            { return null;   }
    if (pw.length < 6)  { return "poor"; }
    if (pw.length < 9)  { return "low";  }
    if (pw.length < 11) { return "med";  }
    if (pw.length < 13) { return "hi";   }
    return "excellent";
  }

  function SetPW() {
    var self = this;

    self.token = ko.observable();
    self.password1 = ko.observable();
    self.password2 = ko.observable();
    self.passwordQuality = ko.computed(function() { return quality(self.password1()); });
    self.ok = ko.computed(function() {
      var t = self.token(),
          p1 = self.password1(),
          p2 = self.password2();
      return t && t.length && p1 && p1.length > 5 && p1 === p2;
    });
    self.success = ko.observable(false);
    self.fail = ko.observable(false);

    self.send = function() {
      ajax
        .post("/api/token/" + self.token())
        .json({password: self.password1()})
        .success(function() { self.success(true).fail(false).password1("").password2(""); })
        .fail(function() { self.success(false).fail(true).password1("").password2(""); $("#setpw input:first").focus(); })
        .call();
    };

    hub.onPageChange("setpw", function(e) { self.token(e.pagePath[0]); });

  }

  function NewCompanyUser() {
    var self = this;

    self.token = ko.observable();

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
    self.passwordQuality = ko.computed(function() { return quality(self.password1()); });
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

  hub.onPageChange("login", recallMe);

  $(function() {
    $("section#login").applyBindings({rememberMe: rememberMe});
    $("section#reset").applyBindings(new Reset());
    $("section#setpw").applyBindings(new SetPW());
    $("section#new-company-user").applyBindings(new NewCompanyUser());
    $("section#invite-company-user").applyBindings(new InviteCompanyUser());
    $("section#accept-company-invitation").applyBindings(new AcceptCompanyInvitation());

    $("#login-button").click(login);
    $("#register-button").click(function() {
      window.location.hash = "!/register";
    });
    $("#login-username").keypress(function(e) {
      if (e.which === 13) {
        $("#login-password").focus();
        return false;
      }
    });
    $("#login-password").keypress(function(e) {
      if (e.which === 13) {
        login();
        return false;
      }
    });
  });

})();
