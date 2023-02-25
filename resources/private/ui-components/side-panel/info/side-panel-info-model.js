LUPAPISTE.SidePanelInfoModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.infoService;

  self.organizationLinks = service.organizationLinks;
  self.infoLinks = service.infoLinks;
  self.showInfoTitle = self.disposedPureComputed( function() {
    return service.canEdit() || _.size( self.infoLinks());
  });
  self.canAdd = service.canEdit;
  self.closePanel = _.wrap( false, lupapisteApp.services.sidePanelService.showInfoPanel );
};
