;(function() {
  "use strict";
  lupapisteApp.models.applicationAuthModel = authorization.create();
  lupapisteApp.models.application = new LUPAPISTE.ApplicationModel(); // model globally available

  lupapisteApp.models.globalAuthModel = authorization.create();
  lupapisteApp.models.globalAuthModel.refreshWithCallback({}, _.partial(hub.send, "global-auth-model-loaded")); // no application bound

  lupapisteApp.models.rootVMO = new LUPAPISTE.RootViewModel();

  lupapisteApp.models.currentUser = new LUPAPISTE.CurrentUser();
})();
