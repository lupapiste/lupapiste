LUPAPISTE.SidePanelInfoModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.infoService;

  self.organizationLinks = service.organizationLinks;
  self.infoLinks = service.infoLinks;
};
