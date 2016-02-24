;(function() {
  "use strict";

  var statusModel = ko.observable();
  var infoModel = {email: ko.observable()};
  var changingModel = {error: ko.observable()};

  var urlPrefix = "/app/" + loc.getCurrentLanguage() + "/welcome";
  var vetumaParams = new LUPAPISTE.VetumaButtonModel(urlPrefix, "vetuma-init-email", "email", "change-email");

  function getToken() {
    var token = pageutil.subPage();
    if (!token) {
      //pageutil.openPage("welcome");
      return;
    }
    return token;
  }

  hub.onPageLoad("email", function() {
    var token = getToken();
    ajax.get("/api/token/" + token).success(function(t) {
      debug(t);
      vetumaParams.token(token);
      vetumaParams.visible(true);
    }).call();

    statusModel(pageutil.lastSubPage());
  });

  hub.onPageLoad("change-email", function() {
    var token = getToken();

    vetuma.getUser(
        function(vetumaData) {
console.log(token);
console.log(vetumaData);
        },
        _.partial(pageutil.openPage, "email", token),
        function(e){$("#change-email-error").text(loc(e.text));}
    );
  });

  $(function(){
    $("#email").applyBindings({status: statusModel, vetuma: vetumaParams, info: infoModel});
    $("#change-email").applyBindings(changingModel);
  });

})();
