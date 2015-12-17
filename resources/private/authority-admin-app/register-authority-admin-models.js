;(function() {
  "use strict";
  lupapisteApp.models.globalAuthModel = authorization.create();
  lupapisteApp.models.globalAuthModel.refresh();
  lupapisteApp.models.currentUser = new LUPAPISTE.CurrentUser();
})();
