;(function() {
  "use strict";
  lupapisteApp.models.application = new LUPAPISTE.ApplicationModel(); // model globally available

  lupapisteApp.models.applicationAuthModel = authorization.create();

  lupapisteApp.models.globalAuthModel = authorization.create();
  lupapisteApp.models.globalAuthModel.refresh();                      // no application bound

  lupapisteApp.models.rootVMO = new LUPAPISTE.RootViewModel();

  lupapisteApp.models.currentUser = new LUPAPISTE.CurrentUser();
})();
