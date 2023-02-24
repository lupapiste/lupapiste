;(function() {
  "use strict";

  window.lupapisteApp = new LUPAPISTE.App({usagePurpose: util.usagePurposeFromUrl(),
                                           startPage: "neighbor-init",
                                           allowAnonymous: true,
                                           showUserMenu: false});

  $(lupapisteApp.domReady);
  $(function() { // dom ready
    $("#neighbor-init").applyBindings({});
    var m = window.location.pathname.match("/app/([a-z]{2})/neighbor/([^/]+)/([^/]+)/([^/]+)");
    if (m) {
      // legacy URL format "redirect" - can be removed after ~ two weeks because old links have two week TTL
      // this redirects browser to use hash based frontend
      var lang = m[1],
        applicationId = m[2],
        neighborId = m[3],
        tokenId = m[4];
      var hash = ["/neighbor", applicationId, neighborId, tokenId].join("/");
      window.location = window.location.protocol + "//" + window.location.host + "/app/" + lang + "/neighbor#!" + hash;
    }
  });
})();
