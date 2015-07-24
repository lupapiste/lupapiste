;(function() {
  "use strict";

  var urlPrefix = "/app/" + loc.getCurrentLanguage() + "/welcome";
  var vetumaParams = {success: "",
                      cancel:  "",
                      error:   ""};

  var afterRegistrationSuccess = function(username, password) {
    // Display ajax loader
    window.location.hash = "!/link-account-3";

    // Auto login
    ajax.postJson("/api/login", {"username": username, "password": password})
      .raw(false)
      .success(function(e) {
        window.parent.location = "/app/" + loc.getCurrentLanguage() + "/" + e.applicationpage;
      })
      .error(function(e) {
        window.location.hash = "!/welcome";
        hub.send("login-failure", e);
      })
      .call();
  };
  var statusModel = new LUPAPISTE.StatusModel();

  var registrationModel = new LUPAPISTE.RegistrationModel("confirm-account-link", vetumaParams, afterRegistrationSuccess, "#link-account-error2",
      ["stamp", "tokenId", "personId", "firstName", "lastName", "email", "confirmEmail", "street", "city", "zip", "phone", "password", "confirmPassword", "street", "zip", "city", "allowDirectMarketing"]);


  function getToken() {
    var token = pageutil.subPage();
    if (!token) {
      window.location.hash = "!/register";
      return false;
    }
    return token;
  }

  function invalidToken() {
    LUPAPISTE.ModalDialog.showDynamicOk(
        loc("register.expired-link.title"),
        loc("register.expired-link.message"),
        {title: loc("ok"),
          fn: function() {
            window.location.hash = "!/register";
            LUPAPISTE.ModalDialog.close();}
        });
  }

  hub.onPageLoad("link-account", function() {
    var token = getToken();

    ajax.query("get-link-account-token", {tokenId: token})
      .success(function(resp) {
        var tokenData = resp.data;
        if (tokenData && tokenData.email) {
          $.get("/api/vetuma",
              {success: urlPrefix + "#!/link-account-2/" + token,
               cancel:  urlPrefix + "#!/link-account/" + token + "/cancel",
               error:   urlPrefix + "#!/link-account/" + token + "/error"},
              function(d) {
                 $("#vetuma-link-account").html(d).find(":submit")
                   .addClass("btn btn-primary")
                   .val(loc("register.action"))
                   .attr("id", "vetuma-linking-init");
              });
        } else {
          invalidToken();
        }
      }).call();

    statusModel.subPage(pageutil.lastSubPage());
  });

  hub.onPageLoad("link-account-2", function() {
    var token = getToken();
    registrationModel.reset();
    ajax.get("/api/vetuma/user")
      .raw(true)
      .success(function(vetumaData) {
        if (vetumaData) {
          ajax.query("get-link-account-token", {tokenId: token})
            .success(function(resp) {
              var tokenData = resp.data;
              if (tokenData && tokenData.email) {
                registrationModel.setVetumaData(vetumaData);
                registrationModel.setPhone(tokenData.phone);
                registrationModel.setEmail(tokenData.email);
              } else {
                invalidToken();
              }
            }).call();
        } else {
          window.location.hash = "!/link-account/" + token;
        }
      })
      .error(function(e){$("#link-account-error2").text(loc(e.text));})
      .call();
  });

  $(function(){
    $("#link-account").applyBindings(statusModel);
    $("#link-account-2").applyBindings(registrationModel.model);
    $("#link-account-3").applyBindings({});
  });
})();
