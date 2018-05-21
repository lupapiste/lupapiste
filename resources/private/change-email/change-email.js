;(function() {
  "use strict";

  function getToken() {
    var token = pageutil.subPage();
    if (!token) {
      pageutil.openPage("welcome");
      return;
    }
    return token;
  }

  function back() {
    pageutil.openPage("email", getToken());
  }

  var initError = ko.observable();
  var statusModel = ko.observable();
  var infoModel = {email: ko.observable()};
  var changingModel = {error: ko.observable(),
                       success: ko.observable(false),
                       back: back,
                       done: ko.observable(false),
                       logout: function() { hub.send("logout"); }};

  var urlPrefix = "/app/" + loc.getCurrentLanguage() + "/welcome";
  var vetumaParams = new LUPAPISTE.VetumaButtonModel(urlPrefix, "vetuma-init-email", "email", "change-email");


  hub.onPageLoad("email", function() {
    var token = getToken();
    ajax.get("/api/token/" + token).raw(false)
      .success(function() {
        vetumaParams.token(token);
        vetumaParams.visible(true);
      })
      .fail(function(response) {
        var err = response.responseJSON.text;
        initError(err);
        if (err === "error.token-used") {
          window.setTimeout(pageutil.openFrontpage, 3000)
        }
      })
      .call();

    statusModel(pageutil.lastSubPage());
  });

  hub.onPageLoad("change-email", function() {
    var token = getToken();

    vetuma.getUser(
        function(vetumaData) {
          ajax.command("change-email", {tokenId: token, stamp: vetumaData.stamp})
            .success(_.partial(changingModel.success, true))
            .error(function(e) {
              changingModel.error(e.text);
            })
            .complete(_.partial(changingModel.done, true))
            .call();
        },
        _.partial(pageutil.openPage, "email", token),
        function(e){
          changingModel.error(e.text);
        });
  });

  hub.onPageLoad("change-email-simple", function() {
    var token = getToken();
    ajax.command("change-email-simple", {tokenId: token})
      .success(function() {
        changingModel.success(true);
      })
      .error(function(r) { changingModel.error(r.text); })
      .fail(_.partial(initError, "error.token-not-found"))
      .call();
  });

  $(function(){
    $("#email").applyBindings({error: initError, status: statusModel, vetuma: vetumaParams, info: infoModel});
    $("#change-email").applyBindings(changingModel);
    $("#change-email-simple").applyBindings(changingModel);
  });

})();
