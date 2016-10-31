;(function() {
  "use strict";
  // Provide application comments for other services
  lupapisteApp.services.commentService = new LUPAPISTE.CommentService();

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
  lupapisteApp.services.calendarService = new LUPAPISTE.CalendarService();
  lupapisteApp.services.attachmentsService = new LUPAPISTE.AttachmentsService();
  lupapisteApp.services.sutiService = new LUPAPISTE.SutiService();
  lupapisteApp.services.infoService = new LUPAPISTE.InfoService();
  lupapisteApp.services.contextService = new LUPAPISTE.ContextService();
  lupapisteApp.services.buildingService = new LUPAPISTE.BuildingService();
  lupapisteApp.services.assignmentService = new LUPAPISTE.AssignmentService(lupapisteApp.models.applicationAuthModel);

})();
