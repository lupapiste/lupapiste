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

  //
  // Initialize:
  //

  hub.onPageChange("login", recallMe);

  $(function() {
    $("section#login").applyBindings({rememberMe: rememberMe});
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
