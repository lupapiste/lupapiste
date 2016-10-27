LUPAPISTE.AttachmentsAccordionsModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;

  self.appModel = lupapisteApp.models.application;
  self.authModel = lupapisteApp.models.applicationAuthModel;

  self.pageName = params.pageName;

  var tagGroupSet  = service.getTagGroups( self.pageName );

  self.groups = tagGroupSet.getTagGroup();

  self.toggleAll = function() {
    tagGroupSet.toggleAll();
  };

};
