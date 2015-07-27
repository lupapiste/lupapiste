;(function() {
  "use strict";

  var urlPrefix = "/app/" + loc.getCurrentLanguage() + "/welcome";

  var VetumaButtonModel = function() {
    var self = this;

    self.id = "vetuma-linking-init";

    self.token = ko.observable();

    self.success = ko.pureComputed(function() {
      return urlPrefix + "#!/link-account-2/" + self.token();
    });
    self.cancel = ko.pureComputed(function() {
      return urlPrefix + "#!/link-account/" + self.token() + "/cancel";
    });
    self.error = ko.pureComputed(function() {
      return urlPrefix + "#!/link-account/" + self.token() + "/error";
    });
    self.y = ko.pureComputed(function() {
      return urlPrefix + "#!/link-account/" + self.token() + "/y";
    });
    self.vtj = ko.pureComputed(function() {
      return urlPrefix + "#!/link-account/" + self.token() + "/vtj";
    });

    self.visible = ko.observable(false);
  };

  var vetumaParams = new VetumaButtonModel();

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
  var statusModel = ko.observable();

  var registrationModel = new LUPAPISTE.RegistrationModel("confirm-account-link", afterRegistrationSuccess, "#link-account-error2",
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
        },
        _.partial(pageutil.openPage, "!/link-account/" + token),
        function(e){$("#link-account-error2").text(loc(e.text));}
    );
  });

  $(function(){
    $("#link-account").applyBindings({status: statusModel, vetuma: vetumaParams});
    $("#link-account-2").applyBindings(registrationModel.model);
    $("#link-account-3").applyBindings({});
  });
})();
