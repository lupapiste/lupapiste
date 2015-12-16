;(function() {
  "use strict";

  window.lupapisteApp = new LUPAPISTE.App({startPage: "users",
                                           allowAnonymous: false,
                                           showUserMenu: true});

  $(lupapisteApp.domReady);
})();
