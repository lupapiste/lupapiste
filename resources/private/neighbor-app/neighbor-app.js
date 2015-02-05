;(function() {
  "use strict";

  var app = new LUPAPISTE.App("", true);
  window.lupapisteApp = app;

  /*
   * This is a hack. Windows FF and IE can't open neighbor page when page information is in hash. They
   * open default page, which would indicate that they do not get the hash at all. Fix that and then
   * remove this.
   */

  app.hashChanged = function() {
    var m = window.location.pathname.match("/app/[a-z]{2}/neighbor/([^/]+)/([^/]+)/([^/]+)");
    if (!m) {
      app.openPage(["404"]);
    } else {
      var applicationId = m[1],
          neighborId = m[2],
          tokenId = m[3];
      app.openPage(["neighbor-show", applicationId, neighborId, tokenId]);
    }
  };

  $(function() {
    app.domReady();
  });

})();
