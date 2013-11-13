;(function() {
  "use strict";

  function login() {
    var username = $("#login-username").val();
    var password = $("#login-password").val();

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
    $("#login-message").text(loc(e.text)).show();
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

  hub.onPageChange("login", function() { $("#login-username:first").focus(); });

  //
  // Initialize:
  //

  $(function() {
    $("section#login").applyBindings({});
    $("section#reset").applyBindings(new Reset());
    $("section#setpw").applyBindings(new SetPW());

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
