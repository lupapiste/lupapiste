;(function() {
  "use strict";
  window.lupapisteApp = new LUPAPISTE.App({usagePurpose: util.usagePurposeFromUrl(),
                                           startPage: "hashbang",
                                           allowAnonymous: true,
                                           logoPath: window.location.pathname + window.location.hash,
                                           showUserMenu: false});
  $(function() {
    lupapisteApp.domReady();
    var model = {continueUrl: lupapisteApp.getHashbangUrl(),
                 continueHandler: lupapisteApp.redirectToHashbang,
                 registerUrl: "/app/" + loc.getCurrentLanguage() + "/welcome#!/register"};
    $("#hashbang").applyBindings(model);
  });

})();
