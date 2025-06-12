;(function() {
  "use strict";

  window.lupapisteApp = new LUPAPISTE.App({usagePurpose: util.usagePurposeFromUrl(),
                                           startPage: "users",
                                           allowAnonymous: false,
                                           showUserMenu: true});

  $(lupapisteApp.domReady);
})();
