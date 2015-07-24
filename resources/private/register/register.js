(function() {
  "use strict";

  var urlPrefix = "/app/" + loc.getCurrentLanguage() + "/welcome";
  var vetumaParams = {success: urlPrefix + "#!/register2",
                      cancel:  urlPrefix + "#!/register/cancel",
                      error:   urlPrefix + "#!/register/error",
                      id:      "vetuma-init"};

  var registrationModel = new LUPAPISTE.RegistrationModel("register-user", _.partial(pageutil.openPage, "!/register3"), "#register-email-error");
  var statusModel = new LUPAPISTE.StatusModel();

  hub.onPageLoad("register", function() {
    statusModel.subPage(pageutil.subPage());
  });

  hub.onPageLoad("register2", function() {
    registrationModel.reset();
    vetuma.getUser(registrationModel.setVetumaData,
                   _.partial(pageutil.openPage, "!/register"),
                   function(e) {$("#register-email-error").text(loc(e.text));});
  });

  $(function(){
    $("#register").applyBindings({status:statusModel, vetuma: vetumaParams});
    $("#register2").applyBindings(registrationModel.model);
    $("#register3").applyBindings(registrationModel.model);
  });

})();
