LUPAPISTE.AttachmentsListingModel = function() {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  
  self.service = lupapisteApp.services.attachmentsService;
  self.attachmentsHierarchy = ko.computed(self.service.getAttachmentsHierarchy);
  self.rollupToggle = ko.observable();
  self.innerToggle = ko.observable();
  self.linkToggle = ko.observable();

  self.rollupStatus = ko.observable( "ok");
};
