;(function() {
  "use strict";

  window.lupapisteApp = new LUPAPISTE.App({usagePurpose: util.usagePurposeFromUrl(),
                                           startPage: "admin",
                                           allowAnonymous: false,
                                           showUserMenu: true});

  $(function() {
    lupapisteApp.domReady();
  });

})();
