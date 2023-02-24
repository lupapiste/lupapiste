;(function() {
  "use strict";
  window.lupapisteApp = new LUPAPISTE.App({usagePurpose: util.usagePurposeFromUrl(),
                                           startPage: "applications",
                                           allowAnonymous: false,
                                           showUserMenu: true});
  $(lupapisteApp.domReady);
})();
