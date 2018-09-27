;(function() {
  "use strict";

  window.lupapisteApp = new LUPAPISTE.App({startPage: "login",
    logoPath: pageutil.frontpage,
    allowAnonymous: true,
    showUserMenu: false});
  $(lupapisteApp.domReady);

  lupapisteApp.models.globalAuthModel = authorization.create();
  lupapisteApp.models.globalAuthModel.refreshWithCallback({}, _.partial(hub.send, "global-auth-model-loaded")); // no application bound
})();
