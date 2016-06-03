;(function() {
  "use strict";
  lupapisteApp.services.organizationTagsService = new LUPAPISTE.OrganizationTagsService();
  lupapisteApp.services.applicationFiltersService = new LUPAPISTE.ApplicationFiltersService();
  lupapisteApp.services.areaFilterService = new LUPAPISTE.AreaFilterService(lupapisteApp.services.applicationFiltersService);
  lupapisteApp.services.handlerFilterService = new LUPAPISTE.HandlerFilterService(lupapisteApp.services.applicationFiltersService);
  lupapisteApp.services.tagFilterService = new LUPAPISTE.TagFilterService(lupapisteApp.services.organizationTagsService, lupapisteApp.services.applicationFiltersService);
  lupapisteApp.services.organizationFilterService = new LUPAPISTE.OrganizationFilterService(lupapisteApp.services.applicationFiltersService);
  lupapisteApp.services.operationFilterService = new LUPAPISTE.OperationFilterService(lupapisteApp.services.applicationFiltersService);
  lupapisteApp.services.publishBulletinService = new LUPAPISTE.PublishBulletinService();
  lupapisteApp.services.documentDataService = new LUPAPISTE.DocumentDataService();
  lupapisteApp.services.sidePanelService = new LUPAPISTE.SidePanelService();
  lupapisteApp.services.accordionService = new LUPAPISTE.AccordionService();
  lupapisteApp.services.fileUploadService = new LUPAPISTE.FileuploadService();
  lupapisteApp.services.verdictAppealService = new LUPAPISTE.VerdictAppealService();
  lupapisteApp.services.scrollService = new LUPAPISTE.ScrollService();
  lupapisteApp.services.ramService = new LUPAPISTE.RamService();
  lupapisteApp.services.attachmentsService = new LUPAPISTE.AttachmentsService();
})();
