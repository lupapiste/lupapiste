(function() {
  "use strict";

  // suomifi-login app doesn't strictly need the LUPAPISTE.App, but
  // we have cljs code where we `(def foo js/lupapisteApp.services.foo)`
  // which crashes otherwise
  window.lupapisteApp = new LUPAPISTE.App({usagePurpose: util.usagePurposeFromUrl(),
                                           logoPath: pageutil.frontpage,
                                           showUserMenu: false});
  $(lupapisteApp.domReady);

  lupapisteApp.models.globalAuthModel = authorization.create();
  lupapisteApp.models.globalAuthModel.refreshWithCallback({}, _.partial(hub.send, "global-auth-model-loaded")); // no application bound

  $(function() {
    // Indirectly mounts the cljs-component even if there are no bindings as such
    // More details in developerguide.md
    $("#suomifi-login-root").applyBindings({});
  });

})();
