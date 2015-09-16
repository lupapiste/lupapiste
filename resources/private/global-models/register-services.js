;(function() {
  "use strict";
  lupapisteApp.services.areaFilterService = new LUPAPISTE.AreaFilterService();
  lupapisteApp.services.operationFilterService = new LUPAPISTE.OperationFilterService();
  lupapisteApp.services.organizationFilterService = new LUPAPISTE.OrganizationFilterService();
  lupapisteApp.services.organizationTagsService = new LUPAPISTE.OrganizationTagsService();
  lupapisteApp.services.tagFilterService = new LUPAPISTE.TagFilterService(lupapisteApp.services.organizationTagsService);
  lupapisteApp.services.handlerFilterService = new LUPAPISTE.HandlerFilterService();
  lupapisteApp.services.applicationFiltersService = new LUPAPISTE.ApplicationFiltersService();
})();
