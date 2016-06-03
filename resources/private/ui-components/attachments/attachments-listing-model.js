LUPAPISTE.AttachmentsListingModel = function() {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  
  self.service = lupapisteApp.services.attachmentsService;
  self.attachmentsHierarchy = ko.computed(self.service.getAttachmentsHierarchy);
};
