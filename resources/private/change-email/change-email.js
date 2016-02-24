;(function() {
  "use strict";

  var statusModel = ko.observable();
  var infoModel = {email: ko.observable()};
  var changingModel = {error: ko.observable(), done: ko.observable()};

  var urlPrefix = "/app/" + loc.getCurrentLanguage() + "/welcome";
  var vetumaParams = new LUPAPISTE.VetumaButtonModel(urlPrefix, "vetuma-init-email", "email", "change-email");

  function getToken() {
    var token = pageutil.subPage();
    if (!token) {
      pageutil.openPage("welcome");
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
    })
    .fail() // TODO
    .call();

    statusModel(pageutil.lastSubPage());
  });

  hub.onPageLoad("change-email", function() {
    var token = getToken();

    vetuma.getUser(
        function(vetumaData) {
          ajax.command("change-email", {tokenId: token, stamp: vetumaData.stamp})
            .success(function() {changingModel.done(true);})
            .call();
        },
        _.partial(pageutil.openPage, "email", token),
        function(e){
          changingModel.error(e.text);
        });
  });

  $(function(){
    $("#email").applyBindings({status: statusModel, vetuma: vetumaParams, info: infoModel});
    $("#change-email").applyBindings(changingModel);
  });

})();
