;(function() {
  "use strict";
  window.lupapisteApp = new LUPAPISTE.App({startPage: "login",
                                           allowAnonymous: true,
                                           showUserMenu: false});
  $(lupapisteApp.domReady);
})();
