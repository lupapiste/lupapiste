LUPAPISTE.SutiAdminModel = function() {
  "use strict";
  var self = this;

  var service = lupapisteApp.services.sutiService;
  service.fetchAdminDetails();
  service.fetchOperations();
  self.enabled = service.sutiEnabled;

  self.www = service.sutiWww;

  self.canEdit = lupapisteApp.models.globalAuthModel.ok( "suti-toggle-enabled");
};
