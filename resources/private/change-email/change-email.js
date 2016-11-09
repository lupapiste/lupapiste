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
                       done: ko.observable(false)};

  var urlPrefix = "/app/" + loc.getCurrentLanguage() + "/welcome";
  var vetumaParams = new LUPAPISTE.VetumaButtonModel(urlPrefix, "vetuma-init-email", "email", "change-email");

  function logoutRedirect(result) {
    var url = util.getIn(LUPAPISTE.config, ["identMethods", "logoutUrl"]);
    if (url) {
      window.location = _.escape(url) + "?return=/app/" + loc.getCurrentLanguage() + "/welcome#!/change-email2/" + result;
    } else {
      pageutil.openPage("register3");
    }
  }


  hub.onPageLoad("email", function() {
    var token = getToken();
    ajax.get("/api/token/" + token).raw(false)
      .success(function() {
        vetumaParams.token(token);
        vetumaParams.visible(true);
      })
      .fail(_.partial(initError, "error.token-not-found"))
      .call();

    statusModel(pageutil.lastSubPage());
  });

  hub.onPageLoad("change-email", function() {
    var token = getToken();

    vetuma.getUser(
        function(vetumaData) {
          ajax.command("change-email", {tokenId: token, stamp: vetumaData.stamp})
            .success(_.partial(logoutRedirect, "ok"))
            .error(function(e) {
              _.partial(logoutRedirect, e.text);
            })
            .complete(_.partial(changingModel.done, true))
            .call();
        },
        _.partial(pageutil.openPage, "email", token),
        function(e){
          changingModel.error(e.text);
        });
  });

  hub.onPageLoad("change-email2", function() {
    var returnStatus = pageutil.lastSubPage();
    if (returnStatus === "ok") {
      changingModel.success(true);
    } else {
      changingModel.error(returnStatus);
    }
  });

  $(function(){
    $("#email").applyBindings({error: initError, status: statusModel, vetuma: vetumaParams, info: infoModel});
    $("#change-email").applyBindings(changingModel);
    $("#change-email2").applyBindings(changingModel);
  });

})();
