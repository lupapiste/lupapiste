(function($) {
  "use strict";

  var rememberMeCookieName = "my-email";

  var rememberMe = ko.observable(false);
  var processing = ko.observable(false);
  var pending = ko.observable(false);
  var passwordVisible = ko.observable(false);
  var username = ko.observable();

  var validUser = ko.pureComputed(function(){
    return _.trim(username()).length > 1;
  });

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

  function clearError() {
    $("#login-message").text("").css("display", "none");
  }

  function login() {
    clearError();

    var username = _.trim($("#login-username").val());
    var password = $("#login-password").val();

    if (rememberMe()) {
      $.cookie(rememberMeCookieName, username.toLowerCase(), { expires: 365, path: "/", secure: LUPAPISTE.config.cookie.secure});
    } else {
      $.removeCookie(rememberMeCookieName, {path: "/"});
    }

    ajax.postJson("/api/login", {"username": username, "password": password})
      .raw(false)
      .processing(processing)
      .pending(pending)
      .success(function(e) {
        var baseUrl = "/app/"
              + ( e.lang || loc.getCurrentLanguage())
              + "/" + e.applicationpage;
        // get the server-stored hashbang or redirect URL to redirect to right page (see web.clj for details)
        ajax.query("redirect-after-login")
          .success(function(e) {
            var redirectLocation = baseUrl;
            if (e.url) {
              redirectLocation = _.startsWith(e.url, "/") ? e.url : baseUrl + "#!/" + e.url;
            }
            window.parent.location = redirectLocation;
          })
          .call();
      })
      .error(function(e) { hub.send("login-failure", e); })
      .call();
  }

  hub.subscribe("login-failure", function(e) {
    $("#login-message").text(loc(e.text)).css("display", "block");
  });

  //
  // Initialize:
  //

  hub.onPageLoad("login", recallMe);

  var checkForSso = function() {
    clearError();
    var passwordElement = document.getElementById("login-password");
    ajax.get("/api/login-sso-uri")
      .param("username", _.trim(username()))
      .success(function(data) {
        if (data.uri) {
          window.location = data.uri;
        } else {
          passwordVisible(true);
          passwordElement.focus();
        }
      })
      .error(function() {
        passwordVisible(true);
        passwordElement.focus();
      })
      .call();
  };

  var handleLoginSubmit = function() {
    if (document.getElementById("login-password").offsetParent !== null) {
      passwordVisible(true);
    }
    if (passwordVisible()) {
      login();
    } else {
      checkForSso();
    }
  };

  $(function() {
    recallMe();
    if (document.getElementById("login")) {
      $("#login").applyBindings({
        rememberMe: rememberMe,
        processing: processing,
        pending: pending,
        handleLoginSubmit: handleLoginSubmit,
        passwordVisible: passwordVisible,
        checkForSso: checkForSso,
        username: username,
        validUser: validUser
      });
      // Refactor to use Knockout at some point. Changes must be synchronized with WordPress theme deployment.
      $("#login-username").keypress(clearError).change(clearError);
      $("#login-password").keypress(clearError).change(clearError);
      $("#register-button").click(function() {
        pageutil.openPage("register");
      });
    }
  });

})(jQuery);
