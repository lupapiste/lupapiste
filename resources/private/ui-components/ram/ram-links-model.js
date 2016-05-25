LUPAPISTE.RamLinksModel = function( params) {
  "use strict";
  var self = this;
  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.attachment = params.attachment;
  self.attachmentId = self.attachment.id();
  self.service = lupapisteApp.services.ramService;
  self.links = ko.observableArray();

  self.showLinks = self.disposedPureComputed( function() {
    return _.size( self.links()) > 1
      && lupapisteApp.models.applicationAuthModel.ok( "ram-linked-attachments");
  });

  self.disposedComputed( function() {
    // We create dependency in order to make sure that the links table
    // updates if file is modified or deleted.
    // This also takes care of the initialization.
    if( self.attachment.versions()) {
      self.service.links( self.attachmentId, self.links );
    }
  });
};
