;(function() {
  "use strict";
  window.lupapisteApp = new LUPAPISTE.App({startPage: "applications",
                                           allowAnonymous: false,
                                           showUserMenu: true});
  $(lupapisteApp.domReady);
})();
