;(function() {
  "use strict";

  window.lupapisteApp = new LUPAPISTE.App({usagePurpose: util.usagePurposeFromUrl(),
                                           startPage: "neighbor-init",
                                           allowAnonymous: true,
                                           showUserMenu: false});

  $(lupapisteApp.domReady);
  $(function() { // dom ready
    $("#neighbor-init").applyBindings({});
  });
})();
