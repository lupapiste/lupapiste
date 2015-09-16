;(function() {
  "use strict";
  lupapisteApp.services.organizationTagsService = new LUPAPISTE.OrganizationTagsService();
  lupapisteApp.services.applicationFiltersService = new LUPAPISTE.ApplicationFiltersService();
  lupapisteApp.services.areaFilterService = new LUPAPISTE.AreaFilterService(lupapisteApp.services.applicationFiltersService);
  lupapisteApp.services.handlerFilterService = new LUPAPISTE.HandlerFilterService(lupapisteApp.services.applicationFiltersService);
  lupapisteApp.services.tagFilterService = new LUPAPISTE.TagFilterService(lupapisteApp.services.organizationTagsService, lupapisteApp.services.applicationFiltersService);
  lupapisteApp.services.organizationFilterService = new LUPAPISTE.OrganizationFilterService(lupapisteApp.services.applicationFiltersService);
  lupapisteApp.services.operationFilterService = new LUPAPISTE.OperationFilterService(lupapisteApp.services.applicationFiltersService);
})();
