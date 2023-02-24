(function() {
  "use strict";

  var urlPrefix = "/app/" + loc.getCurrentLanguage() + "/welcome#!/register";
  var vetumaParams = {success: urlPrefix + "2",
                      cancel:  urlPrefix + "/cancel",
                      error:   urlPrefix + "/error",
                      y:       urlPrefix + "/error",
                      vtj:     urlPrefix + "/error",
                      id:      "vetuma-init"};

  var registrationModel = new LUPAPISTE.RegistrationModel("register-user", _.partial(pageutil.openPage, "register3"));
  var statusModel = ko.observable();

  hub.onPageLoad("register", function() {
    statusModel(pageutil.subPage());
  });

  hub.onPageLoad("register2", function() {
    registrationModel.reset();
    vetuma.getUser(registrationModel.setVetumaData, // onFound
                   _.partial(pageutil.openPage, "register"), // onNotFound
                   _.partial(pageutil.openPage, "eidas"), // onEidasFound
                   function(e) {  // onError
                     registrationModel.model.error( e.text );
                   }
    );
  });

  $(function(){
    $("#register").applyBindings({status:statusModel, vetuma: vetumaParams});
    $("#register2").applyBindings(registrationModel.model);
    $("#register3").applyBindings(registrationModel.model);
    $("#eidas").applyBindings(registrationModel.model);
  });

})();
