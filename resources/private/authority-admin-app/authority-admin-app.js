;(function() {
  "use strict";

  window.lupapisteApp = new LUPAPISTE.App({startPage: "admin",
                                           allowAnonymous: false,
                                           showUserMenu: true});

  $(lupapisteApp.domReady);
})();
