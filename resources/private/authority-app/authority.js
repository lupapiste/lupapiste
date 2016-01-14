;(function() {
  "use strict";
  window.lupapisteApp = new LUPAPISTE.App({startPage: "applications",
                                           allowAnonymous: false,
                                           showUserMenu: true});
  if (!window.parent.LupapisteApi) {
  	window.parent.LupapisteApi = new LupapisteApi();
  }
  $(lupapisteApp.domReady);
})();
