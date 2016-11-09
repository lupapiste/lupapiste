(function() {
  "use strict";

  var urlPrefix = "/app/" + loc.getCurrentLanguage() + "/welcome#!/register";
  var vetumaParams = {success: urlPrefix + "2",
                      cancel:  urlPrefix + "/cancel",
                      error:   urlPrefix + "/error",
                      y:       urlPrefix + "/error",
                      vtj:     urlPrefix + "/error",
                      id:      "vetuma-init"};

  function logoutRedirect() {
    var url = util.getIn(LUPAPISTE.config, ["identMethods", "logoutUrl"]);
    if (url) {
      window.location = _.escape(url) + "?return=/app/" + loc.getCurrentLanguage() + "/welcome#!/register3";
    } else {
      pageutil.openPage("register3");
    }
  }

  var registrationModel = new LUPAPISTE.RegistrationModel("register-user", logoutRedirect, "#register-email-error");
  var statusModel = ko.observable();

  hub.onPageLoad("register", function() {
    statusModel(pageutil.subPage());
  });

  hub.onPageLoad("register2", function() {
    registrationModel.reset();
    vetuma.getUser(registrationModel.setVetumaData,
                   _.partial(pageutil.openPage, "register"),
                   function(e) {$("#register-email-error").text(loc(e.text));});
  });

  $(function(){
    $("#register").applyBindings({status:statusModel, vetuma: vetumaParams});
    $("#register2").applyBindings(registrationModel.model);
  });

})();
