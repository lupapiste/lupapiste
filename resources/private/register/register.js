(function() {
  "use strict";

  var urlPrefix = "/app/" + loc.getCurrentLanguage() + "/welcome#!/register";
  var vetumaParams = {success: urlPrefix + "2",
                      cancel:  urlPrefix + "/cancel",
                      error:   urlPrefix + "/error",
                      y:       urlPrefix + "/error",
                      vtj:     urlPrefix + "/error",
                      id:      "vetuma-init"};

  var registrationModel = new LUPAPISTE.RegistrationModel("register-user", _.partial(pageutil.openPage, "register3"), "#register-email-error");
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
    $("#register3").applyBindings(registrationModel.model);
  });

})();
