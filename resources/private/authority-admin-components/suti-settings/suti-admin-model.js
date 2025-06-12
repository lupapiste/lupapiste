LUPAPISTE.SutiAdminModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.sutiService;
  service.fetchAdminDetails();
  service.fetchOperations();
  self.enabled = service.sutiEnabled;

  self.www = service.sutiWww;

  self.canEdit = self.disposedPureComputed( _.wrap( "suti-toggle-enabled",
                                                    lupapisteApp.models.globalAuthModel.ok ));
};
