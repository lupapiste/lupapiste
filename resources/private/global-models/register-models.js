;(function() {
  "use strict";
  lupapisteApp.models.application = new LUPAPISTE.ApplicationModel(); // model globally available
  lupapisteApp.models.applicationAuthModel = authorization.create();
  lupapisteApp.models.rootVMO = new LUPAPISTE.RootViewModel();
})();