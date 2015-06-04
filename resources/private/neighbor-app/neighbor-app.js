;(function() {
  "use strict";

  window.lupapisteApp = new LUPAPISTE.App({startPage: "",
                                           allowAnonymous: true,
                                           showUserMenu: false});

  /*
   * This is a hack. Windows FF and IE can't open neighbor page when page information is in hash. They
   * open default page, which would indicate that they do not get the hash at all. Fix that and then
   * remove this.
   */

  lupapisteApp.hashChanged = function() {
    var m = window.location.pathname.match("/app/[a-z]{2}/neighbor/([^/]+)/([^/]+)/([^/]+)");
    if (!m) {
      lupapisteApp.openPage(["404"]);
    } else {
      var applicationId = m[1],
          neighborId = m[2],
          tokenId = m[3];
      lupapisteApp.openPage(["neighbor-show", applicationId, neighborId, tokenId]);
    }
  };

  $(lupapisteApp.domReady);

})();
