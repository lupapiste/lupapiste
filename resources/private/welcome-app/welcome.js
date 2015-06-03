;(function() {
  "use strict";
  window.lupapisteApp = new LUPAPISTE.App({startPage: "login",
                                           logoPath: pageutil.frontpage,
                                           allowAnonymous: true,
                                           showUserMenu: false});
  $(lupapisteApp.domReady);
})();
