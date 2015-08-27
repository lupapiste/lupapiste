;(function() {
  "use strict";
  lupapisteApp.models.application = new LUPAPISTE.ApplicationModel(); // model globally available

  lupapisteApp.models.applicationAuthModel = authorization.create();

  lupapisteApp.models.globalAuthModel = authorization.create();
  lupapisteApp.models.globalAuthModel.refresh();                      // no application bound

  lupapisteApp.models.rootVMO = new LUPAPISTE.RootViewModel();

  lupapisteApp.models.currentUser = new LUPAPISTE.CurrentUser();

  lupapisteApp.models.areaFilterService = new LUPAPISTE.AreaFilterService();
  lupapisteApp.models.tagFilterService = new LUPAPISTE.TagFilterService();
  lupapisteApp.models.operationFilterService = new LUPAPISTE.OperationFilterService();
  lupapisteApp.models.organizationFilterService = new LUPAPISTE.OrganizationFilterService();
})();
