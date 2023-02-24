;(function() {
  "use strict";

  var urlPrefix = "/app/" + loc.getCurrentLanguage() + "/welcome";
  var vetumaParams = new LUPAPISTE.VetumaButtonModel(urlPrefix, "vetuma-linking-init", "link-account", "link-account-2");

  var afterRegistrationSuccess = function(username, password) {
    // Display ajax loader
    pageutil.openPage("link-account-3");

    // Auto login
    ajax.postJson("/api/login", {"username": username, "password": password})
      .raw(false)
      .success(function(e) {
        window.parent.location = "/app/" + loc.getCurrentLanguage() + "/" + e.applicationpage;
      })
      .error(function(e) {
        pageutil.openPage("welcome");
        hub.send("login-failure", e);
      })
      .call();
  };
  var statusModel = ko.observable();

  var registrationModel
      = new LUPAPISTE.RegistrationModel("confirm-account-link",
                                        afterRegistrationSuccess,
                                        ["stamp", "tokenId", "personId", "firstName",
                                         "lastName", "email", "confirmEmail", "street",
                                         "city", "zip", "phone", "password", "confirmPassword",
                                         "street", "zip", "city", "allowDirectMarketing"]);

  function getToken() {
    var token = pageutil.subPage();
    if (!token) {
      pageutil.openPage("register");
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
            pageutil.openPage("register");
            LUPAPISTE.ModalDialog.close();}
        });
  }

  hub.onPageLoad("link-account", function() {
    var token = getToken();

    ajax.query("get-link-account-token", {tokenId: token})
      .success(function(resp) {
        var tokenData = resp.data;
        if (tokenData && tokenData.email) {
          vetumaParams.visible(true);
          vetumaParams.token(token);
        } else {
          vetumaParams.visible(false);
          vetumaParams.token("");
          invalidToken();
        }
      }).call();

    statusModel(pageutil.lastSubPage());
  });

  hub.onPageLoad("link-account-2", function() {
    var token = getToken();
    registrationModel.reset();
    vetuma.getUser(
      function(vetumaData) {
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
      }, // onFound
      _.partial(pageutil.openPage, "link-account", token), // onNotFound
      _.partial(pageutil.openPage, "link-account", token), // onEidasFound
      function(e) {  // onError
        registrationModel.model.error( e.text );
      }
    );
  });

  $(function(){
    $("#link-account").applyBindings({status: statusModel, vetuma: vetumaParams});
    $("#link-account-2").applyBindings(registrationModel.model);
    $("#link-account-3").applyBindings({});
  });
})();
