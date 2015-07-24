(function() {
  "use strict";

  var urlPrefix = "/app/" + loc.getCurrentLanguage() + "/welcome";
  var vetumaParams = {success: urlPrefix + "#!/register2",
                      cancel:  urlPrefix + "#!/register/cancel",
                      error:   urlPrefix + "#!/register/error"};

  var registrationModel = new LUPAPISTE.RegistrationModel("register-user", vetumaParams,  function() {window.location.hash = "!/register3";}, "#register-email-error");
  var statusModel = new LUPAPISTE.StatusModel();

  hub.onPageLoad("register", function() {
    $.get("/api/vetuma", vetumaParams, function(d) {
      $("#vetuma-register")
        .html(d).find(":submit").addClass("btn btn-primary")
                                .attr("value",loc("register.action"))
                                .attr("id", "vetuma-init");
    });
    statusModel.subPage(pageutil.subPage());
  });

  hub.onPageLoad("register2", function() {
    registrationModel.reset();
    vetuma.getUser(registrationModel.setVetumaData,
                   _.partial(pageutil.openPage, "!/register"),
                   function(e) {$("#register-email-error").text(loc(e.text));});
  });

  $(function(){
    $("#register").applyBindings(statusModel);
    $("#register2").applyBindings(registrationModel.model);
    $("#register3").applyBindings(registrationModel.model);
  });

})();
