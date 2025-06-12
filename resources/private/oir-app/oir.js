;(function() {
  "use strict";

  window.lupapisteApp = new LUPAPISTE.App({usagePurpose: util.usagePurposeFromUrl(),
                                           startPage: "inforequest",
                                           allowAnonymous: false,
                                           showUserMenu: false});
  $(lupapisteApp.domReady);

})();
